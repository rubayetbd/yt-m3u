package io.github.rubayet123;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException;

import java.io.IOException;

public class OkHttpDownloader extends Downloader {

    OkHttpClient client = new OkHttpClient();

    @Override
    public org.schabi.newpipe.extractor.downloader.Response execute(Request request)
            throws IOException, ReCaptchaException {

        var okReq = new okhttp3.Request.Builder()
                .url(request.url())
                .header("User-Agent","Mozilla/5.0")
                .build();

        Response res = client.newCall(okReq).execute();

        return new org.schabi.newpipe.extractor.downloader.Response(
                res.code(),
                res.message(),
                res.headers().toMultimap(),
                res.body().string(),
                request.url()
        );
    }
}
