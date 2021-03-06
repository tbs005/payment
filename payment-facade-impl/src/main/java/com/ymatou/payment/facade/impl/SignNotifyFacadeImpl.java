/**
 * (C) Copyright 2016 Ymatou (http://www.ymatou.com/).
 *
 * All rights reserved.
 */
package com.ymatou.payment.facade.impl;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson.JSONObject;
import com.ymatou.payment.domain.channel.InstitutionConfig;
import com.ymatou.payment.domain.channel.InstitutionConfigManager;
import com.ymatou.payment.domain.channel.service.signnotify.CmbSignNotifyService;
import com.ymatou.payment.domain.pay.repository.CmbAggrementRepository;
import com.ymatou.payment.domain.pay.repository.CmbPublicKeyRepository;
import com.ymatou.payment.facade.BizException;
import com.ymatou.payment.facade.ErrorCode;
import com.ymatou.payment.facade.SignNotifyFacade;
import com.ymatou.payment.facade.constants.CmbAggrementStatusEnum;
import com.ymatou.payment.facade.constants.PayTypeEnum;
import com.ymatou.payment.facade.model.SignNotifyReq;
import com.ymatou.payment.facade.model.SignNotifyResp;
import com.ymatou.payment.infrastructure.db.model.CmbAggrementPo;
import com.ymatou.payment.infrastructure.util.HttpUtil;
import com.ymatou.payment.integration.common.CmbSignature;
import com.ymatou.payment.integration.model.CmbSignNotifyRequest;
import com.ymatou.payment.integration.model.CmbSignNotifyRequest.SignNoticeData;

/**
 * 签约通知接口实现
 * 
 * @author wangxudong 2016年11月18日 下午8:18:58
 *
 */
@Component("signNotifyFacade")
public class SignNotifyFacadeImpl implements SignNotifyFacade {

    private static Logger logger = LoggerFactory.getLogger(SignNotifyFacadeImpl.class);

    @Resource
    private CmbSignNotifyService cmbSignNotifyService;

    @Resource
    private CmbPublicKeyRepository cmbPublicKeyRepository;

    @Resource
    private InstitutionConfigManager institutionConfigManager;

    @Resource
    private CmbAggrementRepository cmbAggrementRepository;


    @Override
    public SignNotifyResp signNotify(SignNotifyReq req) {
        // 解析报文
        Map<String, String> map = new HashMap<String, String>();
        try {
            map = HttpUtil.parseQueryStringToMap(req.getRawString());;
        } catch (Exception e) {
            throw new BizException(ErrorCode.FAIL, "parse form data when receive cmb sign notify.", e);
        }

        String reqData = map.get("jsonRequestData");
        CmbSignNotifyRequest cmbSigNotifyRequest = JSONObject.parseObject(reqData, CmbSignNotifyRequest.class);

        // 获取公钥 验签
        String pubKey = cmbPublicKeyRepository.getPublicKey(req.getMockHeader());
        String signString = CmbSignature.buildSignString(reqData, "noticeData");
        String sign = cmbSigNotifyRequest.getSign();

        // 验签
        boolean isSignValidate = CmbSignature.isValidSignature(signString, sign, pubKey);
        if (!isSignValidate) {
            throw new BizException(ErrorCode.SIGN_NOT_MATCH, "aggId:" + cmbSigNotifyRequest.getNoticeData().getAgrNo());
        }

        // 验证商户号
        // 防止黑客利用其它商户号的数据伪造支付成功报文
        SignNoticeData signNoticeData = cmbSigNotifyRequest.getNoticeData();
        InstitutionConfig instConfig =
                institutionConfigManager.getConfig(PayTypeEnum.parse(req.getPayType()));
        if (!instConfig.getMerchantId().equals(signNoticeData.getMerchantNo())) {
            throw new BizException(ErrorCode.INVALID_MERCHANT_ID,
                    "aggId:" + signNoticeData.getAgrNo() + ",merchantNo:" + signNoticeData.getMerchantNo());
        }

        CmbAggrementPo aggrementPo = cmbAggrementRepository.getByAggId(Long.parseLong(signNoticeData.getAgrNo()));
        if (aggrementPo == null) {
            throw new BizException(String.format("aggid:%s not exist when sign notify.", signNoticeData.getAgrNo()));
        }

        if (CmbAggrementStatusEnum.INIT.code().equals(aggrementPo.getAggStatus())) {
            if ("SUC0000".equals(signNoticeData.getRspCode())) {
                aggrementPo.setAggStatus(CmbAggrementStatusEnum.SIGN.code());
            } else {
                aggrementPo.setAggStatus(CmbAggrementStatusEnum.FAIL.code());
            }
            aggrementPo.setBankSerialNo(signNoticeData.getNoticeSerialNo());
            aggrementPo.setNoPwdPay(signNoticeData.getNoPwdPay());
            aggrementPo.setRespCode(signNoticeData.getRspCode());
            aggrementPo.setRespMessage(StringUtils.left(signNoticeData.getRspMsg(), 200));
            aggrementPo.setSignDateTime(signNoticeData.getDateTime());
            aggrementPo.setUserPidHash(signNoticeData.getUserPidHash());
            aggrementPo.setUserPidType(signNoticeData.getUserPidType());

            cmbAggrementRepository.update(aggrementPo);

        } else {
            logger.error(String.format("aggid:%d status:%d, not init when sign notify.", aggrementPo.getAggId(),
                    aggrementPo.getAggStatus()));
        }

        SignNotifyResp resp = new SignNotifyResp();
        resp.setSuccess(true);

        return resp;
    }

}
