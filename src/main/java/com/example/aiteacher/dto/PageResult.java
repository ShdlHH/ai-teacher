package com.example.aiteacher.dto;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 统一分页返回体：records 当前页数据，total 总数，page/size 当前分页参数
 */
@Data
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long page;

    private long size;

    public static <T> PageResult<T> of(IPage<T> page) {
        return fill(page, page.getRecords());
    }

    public static <S, T> PageResult<T> map(IPage<S> page, Function<S, T> mapper) {
        return fill(page, page.getRecords().stream().map(mapper).toList());
    }

    public static <T> PageResult<T> empty(long page, long size) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(List.of());
        result.setTotal(0);
        result.setPage(page);
        result.setSize(size);
        return result;
    }

    private static <S, T> PageResult<T> fill(IPage<S> page, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setRecords(records);
        result.setTotal(page.getTotal());
        result.setPage(page.getCurrent());
        result.setSize(page.getSize());
        return result;
    }
}
