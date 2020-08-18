package web.resource;

import context.Resource;
import handler.JerryControllerHandlerMethod;
import org.apache.ibatis.io.Resources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.CopyAntPathMatcher;

import java.io.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author 陈龙
 * @version 1.0
 * @date 2020-08-18 11:16
 */
public class ResourceHandlerRegistry {

    private static Logger logger = LoggerFactory.getLogger(ResourceHandlerRegistry.class);


    private List<String> path = new CopyOnWriteArrayList<>();

    public ResourceHandlerRegistry addResource(String path) {
        this.path.add(path);
        return this;
    }

    public List<String> getPath() {
        return path;
    }

    public boolean isResource(String url) {

        CopyAntPathMatcher copyAntPathMatcher = new CopyAntPathMatcher();
        List<String> list = new ArrayList<>();
        for (String temp : path) {
            if (copyAntPathMatcher.match(temp, url)) {
                list.add(temp);
                break;
            }
        }
        if (list.isEmpty())
            return false;

        return true;
    }

    public File getResource(String url) throws IOException {
        String resource = url;
        InputStream inputStream = null;
        try {
            if (resource.startsWith("/"))
                resource = resource.substring(1);
            inputStream = Resources.getResourceAsStream(resource);
        } catch (IOException e) {
            logger.info("e", e);
        }
        if (inputStream == null) {
            inputStream = Resources.getResourceAsStream("html/404.html");
        }

        File temp = File.createTempFile(resource, Resource.getJerryCfg("md.suffix"));
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(inputStream);
            bos = new BufferedOutputStream(new FileOutputStream(temp));
            int len = 0;
            byte[] buf = new byte[10 * 1024];
            while ((len = bis.read(buf)) != -1) {
                bos.write(buf, 0, len);
            }
            bos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bos != null) bos.close();
                if (bis != null) bis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return temp;
    }
}
