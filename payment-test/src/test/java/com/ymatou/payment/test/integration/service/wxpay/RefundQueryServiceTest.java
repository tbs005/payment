package com.ymatou.payment.test.integration.service.wxpay;

import java.util.HashMap;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.ymatou.payment.integration.common.Signature;
import com.ymatou.payment.integration.model.QueryRefundRequest;
import com.ymatou.payment.integration.model.QueryRefundResponse;
import com.ymatou.payment.integration.service.wxpay.RefundQueryService;
import com.ymatou.payment.test.RestBaseTest;

public class RefundQueryServiceTest extends RestBaseTest {

    @Autowired
    private RefundQueryService refundQueryService;

    @Test
    public void testDoServiceSuccess() throws Exception {
        QueryRefundRequest request = new QueryRefundRequest();
        request.setAppid("wxf51a439c0416f182");
        request.setMch_id("1234079001");
        request.setDevice_info("WEB");
        request.setNonce_str("weixin" + String.valueOf(new Random().nextInt(10)));
        request.setOut_trade_no("407300842881200246326");
        request.setTransaction_id("294be28a62a34aa38b0e38e0cdcbfd7");

        // 加签
        String sign = Signature.getSign(request, "es839gnc8451lp0s943n568xzskjgdbv");
        request.setSign(sign);

        HashMap<String, String> header = new HashMap<>();
        header.put("mock", "1");
        header.put("mockId", "888888");
        QueryRefundResponse response = refundQueryService.doService(request, header);
        Assert.assertNotNull(response);
        Assert.assertEquals(400, response.getTotal_fee());
    }
}
