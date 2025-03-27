package tech.powerjob.official.processors.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONValidator;
import tech.powerjob.worker.core.processor.ProcessResult;
import tech.powerjob.worker.core.processor.TaskContext;
import tech.powerjob.worker.log.OmsLogger;
import lombok.Data;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import tech.powerjob.official.processors.CommonBasicProcessor;
import tech.powerjob.official.processors.util.CommonUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * common http processor
 *
 * @author tjq
 * @author Jiang Jining
 * @since 2021/1/30
 */
public class HttpProcessor extends CommonBasicProcessor {

    /**
     * Default timeout is 60 seconds.
     */
    private static final int DEFAULT_TIMEOUT = 60;
    private static final int HTTP_SUCCESS_CODE = 200;
    private static final String HTTP_SUCCESS_CODE_1 = "0000";
    private static final Map<Integer, OkHttpClient> CLIENT_STORE = new ConcurrentHashMap<>();
    private static final List<String> ALLOWED_METHODS = Arrays.asList("GET", "POST", "PUT", "DELETE");

    @Override
    public ProcessResult process0(TaskContext taskContext) throws Exception {
        OmsLogger omsLogger = taskContext.getOmsLogger();
        HttpParams httpParams = JSON.parseObject(CommonUtils.parseParams(taskContext), HttpParams.class);

        if (httpParams == null) {
            String message = "HTTP请求的参数为空，请检查jobParam配置。";
            omsLogger.error(message);
            return new ProcessResult(false, message);
        }

        if (StringUtils.isEmpty(httpParams.url)) {
            String message = "调用URL地址不能为空！";
            omsLogger.error(message);
            return new ProcessResult(false, message);
        }

        if (!httpParams.url.startsWith("http")) {
            httpParams.url = "http://" + httpParams.url;
        }
        omsLogger.info("调用的URL: {}", httpParams.url);

        // set default method
        if (StringUtils.isEmpty(httpParams.method)) {
            httpParams.method = "GET";
            omsLogger.warn("请求方式为空，使用默认请求方法: GET");
        } else if (ALLOWED_METHODS.stream().noneMatch(httpParams.method::contains)) {
            return new ProcessResult(false, "请求方法仅支持：" + ALLOWED_METHODS);
        } else {
            httpParams.method = httpParams.method.toUpperCase();
            omsLogger.info("请求方法: {}", httpParams.method);
        }

        // set default mediaType
        if (!"GET".equals(httpParams.method)) {
            // set default request body
            if (StringUtils.isEmpty(httpParams.body)) {
                httpParams.body = new JSONObject().toJSONString();
                omsLogger.warn("尝试使用默认的请求body报文:{}", httpParams.body);
            }
            if (JSONValidator.from(httpParams.body).validate() && StringUtils.isEmpty(httpParams.mediaType)) {
                httpParams.mediaType = "application/json";
                omsLogger.warn("尝试使用 “application/json” 的格式");
            }
        }

        // set default timeout
        if (httpParams.timeout == null) {
            httpParams.timeout = DEFAULT_TIMEOUT;
        }
        omsLogger.info("请求超时时间: {} 秒", httpParams.timeout);
        OkHttpClient client = getClient(httpParams.timeout);

        Request.Builder builder = new Request.Builder().url(httpParams.url);
        if (httpParams.headers != null) {
            httpParams.headers.forEach((k, v) -> {
                builder.addHeader(k, v);
                omsLogger.info("添加Header头信息 {}:{}", k, v);
            });
        }

        switch (httpParams.method) {
            case "PUT":
            case "DELETE":
            case "POST":
                MediaType mediaType = MediaType.parse(httpParams.mediaType);
                omsLogger.info("mediaType: {}", mediaType);
                RequestBody requestBody = RequestBody.create(mediaType, httpParams.body);
                builder.method(httpParams.method, requestBody);
                break;
            default:
                builder.get();
        }

        Response response = client.newCall(builder.build()).execute();
//        omsLogger.info("response: {}", response);

        String msgBody = "";
        if (response.body() != null) {
            msgBody = response.body().string();
        }

        int responseCode = response.code();
//        String res = String.format("code:%d, body:%s", responseCode, msgBody);
        boolean success = true;
        if (responseCode != HTTP_SUCCESS_CODE) {
            success = false;
//            omsLogger.error("{} url: {} failed, response code is {}, response body is {}", httpParams.method, httpParams.url, responseCode, msgBody);
        } else {
            JSONObject resultJson = JSON.parseObject(msgBody);
            String resultCode = resultJson.getString("resultCode"), resultMessage = resultJson.getString("resultMessage");
            if (!HTTP_SUCCESS_CODE_1.equals(resultCode)) {
                success = false;
//                omsLogger.error("{} url: {} failed, response code is {}, response body is {}", httpParams.method, httpParams.url, resultCode, resultMessage);
            }
        }
        return new ProcessResult(success, msgBody);
    }

    @Data
    public static class HttpParams {
        /**
         * POST / GET / PUT / DELETE
         */
        private String method;
        /**
         * the request url
         */
        private String url;
        /**
         * application/json
         * application/xml
         * image/png
         * image/jpeg
         * image/gif
         */
        private String mediaType;

        private String body;

        private Map<String, String> headers;

        /**
         * timeout for complete calls
         */
        private Integer timeout;
    }

    private static OkHttpClient getClient(Integer timeout) {
        return CLIENT_STORE.computeIfAbsent(timeout, ignore -> new OkHttpClient.Builder()
                .connectTimeout(Duration.ZERO)
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .callTimeout(timeout, TimeUnit.SECONDS)
                .build());
    }
}
