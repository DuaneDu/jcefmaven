package me.friwi.jcefmaven.fetch;

import me.friwi.jcefmaven.platform.EnumPlatform;
import me.friwi.jcefmaven.version.CefBuildInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PackageDownloader {
    private static final Logger LOGGER = Logger.getLogger(PackageDownloader.class.getName());

    //Do not waste your time on these credentials.
    //Its a PAT that can only read packages. If you want to see them too,
    //go here: https://github.com/orgs/jcefmaven/packages ;)
    //The token is encoded to prevent github from deleting it.
    private static final String USER = "jcefmavenbot";
    private static final String TOKEN = new String(
            Base64.getDecoder().decode("Z2hwX0JWblZMMmlpWG9sR2VneHBRYWZZeUVCUnlTOWl3djJ1UHJGag=="), StandardCharsets.UTF_8);
    private static final String DOWNLOAD_URL = "https://maven.pkg.github.com/jcefmaven/jcefmaven/me/friwi/" +
            "jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar";
    private static final String FALLBACK_DOWNLOAD_URL = "https://repo.maven.apache.org/maven2/me/friwi/" +
            "jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar";

    private static final int BUFFER_SIZE = 16*1024;

    public static void downloadNatives(CefBuildInfo info, EnumPlatform platform, File destination, Consumer<Float> progressConsumer) throws IOException {
        Objects.requireNonNull(info, "info cannot be null");
        Objects.requireNonNull(platform, "platform cannot be null");
        Objects.requireNonNull(destination, "destination cannot be null");
        Objects.requireNonNull(progressConsumer, "progressConsumer cannot be null");
        //Create target file
        if(!destination.createNewFile()){
            throw new IOException("Could not create target file "+destination.getAbsolutePath());
        }
        //Open connection with authentication to github
        URL url = new URL(DOWNLOAD_URL
                .replace("{platform}", platform.getIdentifier())
                .replace("{tag}", info.getReleaseTag()));
        HttpURLConnection uc = (HttpURLConnection) url.openConnection();
        String userpass = USER + ":" + TOKEN;
        String basicAuth = "Basic " + new String(Base64.getEncoder().encode(userpass.getBytes()), StandardCharsets.UTF_8);
        uc.setRequestProperty ("Authorization", basicAuth);
        InputStream in = null;
        try {
            in = uc.getInputStream();
        }catch (IOException e){
            //Ignore error, will try fallback in follow up code
            uc.disconnect();
        }
        if(uc.getResponseCode()!=200){
            //Error while requesting from github, use maven central instead
            //(only accept code 200 to make sure that there is no partial response downloaded, like a redirection)
            LOGGER.log(Level.WARNING, "Requesting from sonatype due to "+uc.getResponseCode()+" from github");
            url = new URL(FALLBACK_DOWNLOAD_URL
                    .replace("{platform}", platform.getIdentifier())
                    .replace("{tag}", info.getReleaseTag()));
            uc = (HttpURLConnection) url.openConnection();
            in = uc.getInputStream();
        }
        long length = uc.getContentLengthLong();
        //Transfer data
        FileOutputStream fos = new FileOutputStream(destination);
        long progress = 0;
        progressConsumer.accept(0f);
        byte[] buffer = new byte[BUFFER_SIZE];
        long transferred = 0;
        int r;
        while((r = in.read(buffer))>0){
            fos.write(buffer, 0, r);
            transferred += r;
            long newprogress = transferred * 100 / length;
            if(newprogress>progress){
                progress = newprogress;
                progressConsumer.accept((float) progress);
            }
        }
        fos.flush();
        //Cleanup
        fos.close();
        in.close();
        uc.disconnect();
    }
}