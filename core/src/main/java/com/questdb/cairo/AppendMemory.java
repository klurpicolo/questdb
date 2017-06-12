package com.questdb.cairo;

import com.questdb.misc.Files;
import com.questdb.std.ObjectFactory;
import com.questdb.std.str.LPSZ;

public class AppendMemory extends VirtualMemory {
    public static final ObjectFactory<AppendMemory> FACTORY = AppendMemory::new;

    private long fd = -1;
    private long pageAddress = 0;
    private int page;

    public AppendMemory(LPSZ name, int pageSize, long size) {
        of(name, pageSize, size);
    }

    public AppendMemory() {
    }

    @Override
    public void close() {
        long sz = size();
        super.close();
        if (pageAddress != 0) {
            Files.munmap(pageAddress, pageSize);
            pageAddress = 0;
        }
        if (fd != -1) {
            Files.truncate(fd, sz);
            Files.close(fd);
            fd = -1;
        }
    }

    @Override
    protected void addPage(long address) {
    }

    @Override
    protected long allocateNextPage() {
        if (pageAddress != 0) {
            release(pageAddress);
        }
        pageAddress = mapPage(++page);
        if (pageAddress == -1) {
            throw new RuntimeException("Cannot mmap");
        }
        return pageAddress;
    }

    @Override
    protected int getMaxPage() {
        return page + 1;
    }

    @Override
    protected long getPageAddress(int page) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected void release(long address) {
        Files.munmap(address, pageSize);
    }

    public long getFd() {
        return fd;
    }

    public final void of(LPSZ name, int pageSize, long size) {
        of(name, pageSize);
        setSize(size);
    }

    public final void of(LPSZ name, int pageSize) {
        close();
        setPageSize(pageSize);
        fd = Files.openRW(name);
        if (fd == -1) {
            throw new RuntimeException("cannot open file");
        }
    }

    public final void setSize(long size) {
        if (pageAddress != 0) {
            Files.munmap(pageAddress, pageSize);
            pageAddress = 0;
        }

        page = pageIndex(size);
        updateLimits(page + 1, pageAddress = mapPage(page));
        skip((size - pageOffset(page)));
    }

    public void truncate() {
        if (pageAddress != 0) {
            Files.munmap(pageAddress, pageSize);
        }
        Files.truncate(fd, pageSize);
        page = 0;
        updateLimits(page + 1, pageAddress = mapPage(page));
    }

    private long mapPage(int page) {
        long target = pageOffset(page + 1);
        long fileSize = Files.length(fd);
        if (fileSize < target) {
            if (!Files.truncate(fd, target)) {
                throw new RuntimeException("Cannot resize file");
            }
        }
        return Files.mmap(fd, pageSize, pageOffset(page), Files.MAP_RW);
    }

}