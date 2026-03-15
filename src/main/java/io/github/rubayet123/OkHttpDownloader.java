package io.github.rubayet123;

import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.downloader.Request;
import org.schabi.newpipe.extractor.downloader.Response;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okhttp3.Headers;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OkHttpDownloader extends Downloader {

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public Response execute(Request request) throws IOException, ReCaptchaException {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), request.dataToSend() != null ? RequestBody.create(null, request.dataToSend()) : null)
                .header("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36");

        request.headers().forEach((k,v) -> builder.header(k, v[0]));

        okhttp3.Response res = client.newCall(builder.build()).execute();

        if(res.code() == 429) throw new ReCaptchaException("reCAPTCHA required at " + request.url(), request.url());

        String body = res.body() != null ? res.body().string() : null;
        return new Response(res.code(), res.message(), res.headers().toMultimap(), body, request.url());
    }
}
