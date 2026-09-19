package awa.uxu.xigua.geyserupdate.common;

import java.io.IOException;
import java.io.InputStream;

public record DownloadStream(InputStream inputStream, long contentLength) implements AutoCloseable {
    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
