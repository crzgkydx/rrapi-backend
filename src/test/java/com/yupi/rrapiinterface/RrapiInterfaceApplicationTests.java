package com.yupi.rrapiinterface;

import com.yupi.rrapiclientsdk.client.RrApiClient;
import com.yupi.rrapiclientsdk.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class RrapiInterfaceApplicationTests {

    // 注入一个名为rrApiClient的Bean
    @Resource
    private RrApiClient rrApiClient;
    // 表示这是一个测试方法
    @Test
    void contextLoads() {
        // 调用yuApiClient的getNameByGet方法，并传入参数"rr"，将返回的结果赋值给result变量
        String result = rrApiClient.getNameByGet("rr");
        // 创建一个User对象
        User user = new User();
        // 设置User对象的username属性为"liyupi"
        user.setUsername("liyupi");
        // 调用yuApiClient的getUserNameByPost方法，并传入user对象作为参数，将返回的结果赋值给usernameByPost变量
        String usernameByPost = rrApiClient.getUserNameByPost(user);
        // 打印result变量的值
        System.out.println(result);
        // 打印usernameByPost变量的值
        System.out.println(usernameByPost);
    }

}
