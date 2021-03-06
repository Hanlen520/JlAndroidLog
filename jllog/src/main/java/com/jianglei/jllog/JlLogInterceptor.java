package com.jianglei.jllog;

import android.provider.MediaStore;

import com.jianglei.jllog.aidl.NetInfoVo;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;
import okio.BufferedSource;

/**
 * @author jianglei
 *         为okhttp提供拦截器
 */

public class JlLogInterceptor implements Interceptor {
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        NetInfoVo netInfoVo = new NetInfoVo();
        netInfoVo.setSuccessful(true);
        HttpUrl httpUrl = request.url();
        //设置url
        netInfoVo.setUrl(httpUrl.toString().split("\\?")[0]);
        //设置Query参数
        Map<String, String> queryParams = new HashMap<>(5);
        for (int i = 0; i < httpUrl.querySize(); ++i) {
            queryParams.put(httpUrl.queryParameterName(i), httpUrl.queryParameterValue(i));
        }
        netInfoVo.setRequsetUrlParams(queryParams);
        //设置Header
        Map<String, String> headers = new HashMap<>(5);
        for (String name : request.headers().names()) {
            headers.put(name, request.header(name));
        }
        netInfoVo.setRequestHeader(headers);

        //设置post参数
        RequestBody requestBody = request.body();
        if (requestBody != null && requestBody instanceof FormBody) {
            Map<String, String> postParams = new HashMap<>(5);
            FormBody formRequestBody = (FormBody) requestBody;

            for (int i = 0; i < formRequestBody.size(); ++i) {
                postParams.put(formRequestBody.name(i), formRequestBody.value(i));
            }
            netInfoVo.setRequestForm(postParams);
        }
        Response response;

        try {
            response = chain.proceed(request);
        } catch (IOException e) {
            netInfoVo.setErrorMsg(getErrorMsg(e));
            netInfoVo.setSuccessful(false);
            JlLog.notifyNetInfo(netInfoVo);
            throw new IOException(e);
        }
        if (!JlLog.isIsDebug()) {
            return response;
        }

        //设置返回信息json
        ResponseBody responseBody = response.body();
        if (responseBody == null) {
            netInfoVo.setResponseJson("No ResponseBody!");
        } else {
            MediaType mediaType = responseBody.contentType();
            if (mediaType == null) {
                netInfoVo.setResponseJson("No content-type!");
            } else if (mediaType.toString().toLowerCase(Locale.getDefault()).contains("text/plain") ||
                    mediaType.toString().toLowerCase(Locale.getDefault()).contains("application/json")) {
                BufferedSource source = responseBody.source();
                source.request(Long.MAX_VALUE);
                Buffer buffer = source.buffer();
                netInfoVo.setResponseJson(buffer.clone().readString(Charset.forName("UTF-8")));
                JlLog.notifyNetInfo(netInfoVo);
            }
        }
        return response;
    }

    /**
     * 请求体转String
     *
     * @param request
     * @return
     */
    private static String bodyToString(final RequestBody request) {

        try {
            final Buffer buffer = new Buffer();
            request.writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }

    private String getErrorMsg(Throwable e){
        Writer writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        e.printStackTrace(pw);
        Throwable cause = e.getCause();
        // 循环着把所有的异常信息写入writer中
        while (cause != null) {
            cause.printStackTrace(pw);
            cause = cause.getCause();
        }
        pw.close();// 记得关闭
        return writer.toString();
    }
}
