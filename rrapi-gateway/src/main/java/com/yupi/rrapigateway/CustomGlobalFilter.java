package com.yupi.rrapigateway;

import com.yupi.rrapiclientsdk.utils.SignUtils;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static jdk.nashorn.internal.runtime.regexp.joni.Config.log;

@Slf4j
@Component
public class CustomGlobalFilter implements GlobalFilter, Ordered {

    private static final List<String> IP_WHITE_LIST = Arrays.asList("127.0.0.1");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //1 用户发送请求到 API 网关
        //2 请求日志
        ServerHttpRequest request = exchange.getRequest();
        log.info("请求唯一标识： " +request.getId());
        log.info("请求路径： " +request.getPath().value());
        log.info("请求方法： " +request.getMethod());
        log.info("请求参数： " +request.getQueryParams());
        String sourceAddress = request.getLocalAddress().getHostString();
        log.info("请求来源地址： " +sourceAddress);
        log.info("请求来源地址： " +request.getRemoteAddress());
        //拿到响应对象
        ServerHttpResponse response = exchange.getResponse();

        //3 （黑白名单）
        if (!IP_WHITE_LIST.contains(sourceAddress)) {
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return response.setComplete();
        }

        //4 用户鉴权（判断 ak、sk 是否合法）
        // 从请求头中获取参数
        HttpHeaders headers = request.getHeaders();
        String accessKey = headers.getFirst("accessKey");
        String nonce = headers.getFirst("nonce");
        String timestamp = headers.getFirst("timestamp");
        String sign = headers.getFirst("sign");
        String body = headers.getFirst("body");

        // todo 实际情况应该是去数据库中查是否已分配给用户
        if (!"rr".equals(accessKey)){
            return handleNoAuth(response);
        }
        // 直接校验如果随机数大于1万，则抛出异常，并提示"无权限"
        if (Long.parseLong(nonce) > 10000L) {
            return handleNoAuth(response);
        }

        // todo 时间和当前时间不能超过5分钟
        // 首先,获取当前时间的时间戳,以秒为单位
        // System.currentTimeMillis()返回当前时间的毫秒数，除以1000后得到当前时间的秒数。
        Long currentTime = System.currentTimeMillis() / 1000;
        // 定义一个常量FIVE_MINUTES,表示五分钟的时间间隔(乘以60,将分钟转换为秒,得到五分钟的时间间隔)。
        final Long FIVE_MINUTES = 60 * 5L;
        // 判断当前时间与传入的时间戳是否相差五分钟或以上
        // Long.parseLong(timestamp)将传入的时间戳转换成长整型
        // 然后计算当前时间与传入时间戳之间的差值(以秒为单位),如果差值大于等于五分钟,则返回true,否则返回false
        if ((currentTime - Long.parseLong(timestamp)) >= FIVE_MINUTES) {
            // 如果时间戳与当前时间相差五分钟或以上，调用handleNoAuth(response)方法进行处理
            return handleNoAuth(response);
        }
        String serverSign = SignUtils.genSign(body, "abcdefgh");
        // 如果生成的签名不一致，则抛出异常，并提示"无权限"
        if (!sign.equals(serverSign)) {
            return handleNoAuth(response);
        }

        //5 请求的模拟接口是否存在？
        // todo 从数据可中查询接口是否存在，以及请求方法是否匹配（还可以检验请求参数）
        //6 请求转发，调用模拟接口
        Mono<Void> filter = chain.filter(exchange);
        //7 响应日志
        return handleResponse(exchange, chain);

//
//        return filter;
    }

    /**
     * 处理响应
     *
     * @param exchange
     * @param chain
     * @return
     */
    public Mono<Void> handleResponse(ServerWebExchange exchange, GatewayFilterChain chain) {
        try {
            // 获取原始的响应对象
            ServerHttpResponse originalResponse = exchange.getResponse();
            // 获取数据缓冲工厂
            DataBufferFactory bufferFactory = originalResponse.bufferFactory();
            // 获取响应的状态码
            HttpStatus statusCode = originalResponse.getStatusCode();

            // 判断状态码是否为200 OK(按道理来说,现在没有调用,是拿不到响应码的,对这个保持怀疑 沉思.jpg)
            if(statusCode == HttpStatus.OK) {
                // 创建一个装饰后的响应对象(开始穿装备，增强能力)
                ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {

                    // 重写writeWith方法，用于处理响应体的数据
                    // 这段方法就是只要当我们的模拟接口调用完成之后,等它返回结果，
                    // 就会调用writeWith方法,我们就能根据响应结果做一些自己的处理
                    @Override
                    public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
                        log.info("body instanceof Flux: {}", (body instanceof Flux));
                        // 判断响应体是否是Flux类型
                        if (body instanceof Flux) {
                            Flux<? extends DataBuffer> fluxBody = Flux.from(body);
                            // 返回一个处理后的响应体
                            // (这里就理解为它在拼接字符串,它把缓冲区的数据取出来，一点一点拼接好)
                            return super.writeWith(fluxBody.map(dataBuffer -> {
                                //8 todo 调用成功，接口调用次数 + 1 incokeCount
                                byte[] content = new byte[dataBuffer.readableByteCount()];
                                dataBuffer.read(content);
                                DataBufferUtils.release(dataBuffer);//释放掉内存
                                // 构建日志
                                StringBuilder sb2 = new StringBuilder(200);
                                List<Object> rspArgs = new ArrayList<>();
                                rspArgs.add(originalResponse.getStatusCode());
                                //rspArgs.add(requestUrl);
                                String data = new String(content, StandardCharsets.UTF_8);//data
                                sb2.append(data);
                                // 将处理后的内容重新包装成DataBuffer并返回
                                log.info("响应结果：" + data);
                                return bufferFactory.wrap(content);
                            }));
                        } else {
                            //9 调用失败，返回一个规范的错误码
                            log.error("<--- {} 响应code异常", getStatusCode());
                        }
                        return super.writeWith(body);
                    }
                };
                // 对于200 OK的请求,将装饰后的响应对象传递给下一个过滤器链,并继续处理(设置repsonse对象为装饰过的)
                return chain.filter(exchange.mutate().response(decoratedResponse).build());
            }
            // 对于非200 OK的请求，直接返回，进行降级处理
            return chain.filter(exchange);
        }catch (Exception e){
            // 处理异常情况，记录错误日志
            log.error("网关处理异常" + e);
            return chain.filter(exchange);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }

    public Mono<Void> handleNoAuth(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.FORBIDDEN);
        return response.setComplete();
    }


    public Mono<Void> handleInvokeError(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        return response.setComplete();
    }
}