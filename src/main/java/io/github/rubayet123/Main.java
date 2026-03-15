package io.github.rubayet123;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
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

    static class CacheEntry {
        String channel_id;
        String video_id;
        long timestamp;
    }

    // Load cache.json
    static Map<String, CacheEntry> loadCache() {
        try {
            File f = new File("cache.json");
            if(!f.exists()) return new HashMap<>();
            String json = new String(java.nio.file.Files.readAllBytes(f.toPath()));
            Type type = new TypeToken<List<CacheEntry>>(){}.getType();
            List<CacheEntry> list = new Gson().fromJson(json, type);
            Map<String, CacheEntry> map = new HashMap<>();
            for(CacheEntry e : list) map.put(e.channel_id, e);
            return map;
        } catch(Exception e){ return new HashMap<>(); }
    }

    static void saveCache(Map<String, CacheEntry> map) {
        try {
            List<CacheEntry> list = new ArrayList<>(map.values());
            String json = new Gson().toJson(list);
            java.nio.file.Files.write(new File("cache.json").toPath(), json.getBytes());
        } catch(Exception ignored){}
    }

    static boolean cacheValid(CacheEntry c){
        long age = System.currentTimeMillis()/1000 - c.timestamp;
        return age < 21600; // 6 hours
    }

    // Resolve /live page with retry
    public static String resolveVideo(String channelId) {
        String[] userAgents = {
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 13_5) AppleWebKit/605.1.15 Chrome/120.0.0.0 Safari/605.1.15",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
        };

        int retries = 3;
        long backoff = 2000; // start 2 sec

        for(int attempt=0; attempt<retries; attempt++){
            try{
                Request req = new Request.Builder()
                        .url("https://www.youtube.com/channel/" + channelId + "/live")
                        .header("User-Agent", userAgents[new Random().nextInt(userAgents.length)])
                        .build();
                Response res = client.newCall(req).execute();
                String finalUrl = res.request().url().toString();
                String body = res.body().string();
                res.close();

                if(finalUrl.contains("watch?v=")) return finalUrl;

                // Extract videoId from HTML
                var regex = "\"videoId\":\"([a-zA-Z0-9_-]{11})\"";
                var matcher = java.util.regex.Pattern.compile(regex).matcher(body);
                if(matcher.find()) return "https://www.youtube.com/watch?v=" + matcher.group(1);

            } catch(Exception e){
                try{ Thread.sleep(backoff); } catch(Exception ignored){}
                backoff *= 2;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {

        NewPipe.init(new OkHttpDownloader());

        String jsonUrl = "https://raw.githubusercontent.com/Rubayet123/BDLive/main/youtube/channels.json";
        Request req = new Request.Builder().url(jsonUrl).build();
        String json = client.newCall(req).execute().body().string();
        Type listType = new TypeToken<List<Channel>>(){}.getType();
        List<Channel> channels = new Gson().fromJson(json, listType);

        ExecutorService pool = Executors.newFixedThreadPool(5); // limit to 5 parallel threads
        Map<String, CacheEntry> cache = loadCache();
        List<String> results = Collections.synchronizedList(new ArrayList<>());

        List<Future<?>> futures = new ArrayList<>();

        for(Channel ch : channels){
            futures.add(pool.submit(() -> {
                try{
                    String videoUrl = null;
                    CacheEntry cached = cache.get(ch.channel_id);
                    if(cached != null && cacheValid(cached)){
                        videoUrl = "https://www.youtube.com/watch?v=" + cached.video_id;
                        System.out.println("CACHE "+ch.name);
                    } else {
                        videoUrl = resolveVideo(ch.channel_id);
                    }

                    if(videoUrl == null) return;

                    StreamInfo info = StreamInfo.getInfo(ServiceList.YouTube, videoUrl);
                    String hls = info.getHlsUrl();
                    if(hls == null) return;

                    results.add("#EXTINF:-1 tvg-id=\""+ch.channel_id+"\" tvg-name=\""+ch.name+"\" tvg-logo=\""+ch.logo+"\" group-title=\""+ch.category+"\","+ch.name+"\n"+hls);

                    // update cache
                    CacheEntry entry = new CacheEntry();
                    entry.channel_id = ch.channel_id;
                    entry.video_id = videoUrl.substring(videoUrl.indexOf("v=")+2);
                    entry.timestamp = System.currentTimeMillis()/1000;
                    cache.put(ch.channel_id, entry);

                    System.out.println("OK "+ch.name);

                } catch(Exception e){
                    System.out.println("OFFLINE "+ch.name);
                }
            }));
        }

        // wait all tasks
        for(Future<?> f : futures) f.get();

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.MINUTES);

        // write playlist
        StringBuilder m3u = new StringBuilder("#EXTM3U\n");
        for(String r : results) m3u.append(r).append("\n");

        FileWriter fw = new FileWriter(new File("playlist.m3u"));
        fw.write(m3u.toString());
        fw.close();

        saveCache(cache);
        System.out.println("Playlist and cache updated!");
    }
}
