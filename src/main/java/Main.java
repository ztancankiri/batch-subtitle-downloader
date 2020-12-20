import com.github.wtekiela.opensub4j.api.OpenSubtitlesClient;
import com.github.wtekiela.opensub4j.impl.OpenSubtitlesClientImpl;
import com.github.wtekiela.opensub4j.response.*;
import org.apache.xmlrpc.XmlRpcException;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

public class Main {

    public static void main(String[] args) throws IOException, XmlRpcException {
        URL serverUrl = new URL("https", "api.opensubtitles.org", 443, "/xml-rpc");
        OpenSubtitlesClient osClient = new OpenSubtitlesClientImpl(serverUrl);

        Response response = osClient.login("doyog72244", "123456789", "en", "TemporaryUserAgent");

        if (response.getStatus().getCode() != ResponseStatus.OK.getCode()) {
            System.err.println("Error while connecting to the server!");
            return;
        }

        if (!osClient.isLoggedIn()) {
            System.err.println("Error while logging in!");
            return;
        }

        String rootPath = args[1];
        File directory = new File(rootPath);

        for (File fileEntry : Objects.requireNonNull(directory.listFiles())) {
            String fileName = fileEntry.getName();
            if (fileName.contains(".mp4")) {
                String baseFileName = fileName.replace(".mp4", "");
                Path newDir = Files.createDirectory(Paths.get(rootPath, baseFileName));
                Files.move(Paths.get(rootPath, fileName), Paths.get(String.valueOf(newDir), fileName));
                String newFilePath = String.valueOf(Paths.get(String.valueOf(newDir), fileName));
                downloadSubtitle(osClient, args[0], newFilePath);
            }
        }

        osClient.logout();
    }

    private static void downloadSubtitle(OpenSubtitlesClient client, String language, String videoFilePath) throws IOException, XmlRpcException {
        ListResponse<SubtitleInfo> searchResponse = client.searchSubtitles(language, new File(videoFilePath));
        List<SubtitleInfo> subtitles = searchResponse.getData();

        if (subtitles != null && subtitles.size() > 0) {
            SubtitleInfo subtitleInfo = subtitles.get(0);
            String subtitleDownloadURL = subtitleInfo.getDownloadLink();

            byte[] gz = downloadUrl(subtitleDownloadURL);
            byte[] srt = decompress(gz);
            byte[] srtFile = toUTF8BOM(srt);

            int posLastDot = videoFilePath.lastIndexOf(".");
            String subtitleFilePath = videoFilePath.substring(0, posLastDot) + ".srt";

            OutputStream out = new FileOutputStream(subtitleFilePath);
            out.write(srtFile);
            out.close();
        }
        else {
            System.err.println("Could not file subtitle in \"" + language + "\" for " + videoFilePath);
        }
    }

    private static byte[] downloadUrl(String url) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            byte[] chunk = new byte[4096];
            int bytesRead;
            InputStream stream = new URL(url).openStream();

            while ((bytesRead = stream.read(chunk)) > 0) {
                outputStream.write(chunk, 0, bytesRead);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return outputStream.toByteArray();
    }

    private static byte[] decompress(byte[] source) throws IOException {
        GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(source));
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[4096];
        int len;
        while ((len = gzipInputStream.read(buffer)) > 0) {
            byteArrayOutputStream.write(buffer, 0, len);
        }

        byte[] result = byteArrayOutputStream.toByteArray();

        byteArrayOutputStream.close();
        gzipInputStream.close();

        return result;
    }

    private static byte[] toUTF8BOM(byte[] source) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(source);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Reader reader = new InputStreamReader(in, Charset.forName("ISO-8859-9"));
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);

        writer.write('\uFEFF');
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            writer.write(buffer, 0, read);
        }

        byte[] result = out.toByteArray();

        writer.close();
        out.close();
        reader.close();
        in.close();

        return result;
    }

}