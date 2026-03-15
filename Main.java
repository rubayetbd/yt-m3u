package io.github.rubayet123;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.*;

public class Main {

    static OkHttpClient client = new OkHttpClient();

    static class Channel {

        String name;
        String channel_id;
        String category;
        String logo;
    }

    public static String resolveVideo(String channelId) {

        try {

            String url = "https://www.youtube.com/channel/" + channelId + "/live";

            Request req = new Request.Builder()
                    .url(url)
                    .header("User-Agent","Mozilla/5.0")
                    .build();

            var res = client.newCall(req).execute();

            String finalUrl = res.request().url().toString();
            String body = res.body().string();

            if(finalUrl.contains("watch?v="))
                return finalUrl;

            var regex = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"";

            var matcher = java.util.regex.Pattern
                    .compile(regex)
                    .matcher(body);

            if(matcher.find())
                return "https://www.youtube.com/watch?v=" + matcher.group(1);

        } catch (Exception ignored){}

        return null;
    }

    public static void main(String[] args) throws Exception {

        NewPipe.init(new OkHttpDownloader());

        String jsonUrl =
                "https://raw.githubusercontent.com/Rubayet123/BDLive/main/youtube/channels.json";

        Request req = new Request.Builder().url(jsonUrl).build();

        String json = client.newCall(req).execute().body().string();

        Type listType = new TypeToken<List<Channel>>(){}.getType();

        List<Channel> channels =
                new Gson().fromJson(json,listType);

        ExecutorService pool =
                Executors.newFixedThreadPool(5);

        List<String> results =
                Collections.synchronizedList(new ArrayList<>());

        for(Channel ch : channels){

            pool.submit(() -> {

                try{

                    String video =
                            resolveVideo(ch.channel_id);

                    if(video == null)
                        return;

                    StreamInfo info =
                            StreamInfo.getInfo(
                                    ServiceList.YouTube,
                                    video
                            );

                    String hls = info.getHlsUrl();

                    if(hls == null)
                        return;

                    String line =
                            "#EXTINF:-1 tvg-id=\""+ch.channel_id+
                            "\" tvg-name=\""+ch.name+
                            "\" tvg-logo=\""+ch.logo+
                            "\" group-title=\""+ch.category+
                            "\","+ch.name+"\n"+hls;

                    results.add(line);

                    System.out.println("OK "+ch.name);

                }catch(Exception e){

                    System.out.println("OFFLINE "+ch.name);

                }

            });

        }

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);

        StringBuilder m3u =
                new StringBuilder("#EXTM3U\n");

        for(String r : results)
            m3u.append(r).append("\n");

        FileWriter fw =
                new FileWriter(new File("playlist.m3u"));

        fw.write(m3u.toString());

        fw.close();

        System.out.println("Playlist created");
    }
}
