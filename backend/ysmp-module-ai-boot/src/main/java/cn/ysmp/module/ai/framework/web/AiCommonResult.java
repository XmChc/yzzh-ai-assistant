package cn.ysmp.module.ai.framework.web;

import lombok.Data;

/**
 * SDK 自带统一返回，避免强制依赖宿主 CommonResult。
 * 宿主若有全局包装可再适配。
 */
@Data
public class AiCommonResult<T> {

    private Integer code;
    private String msg;
    private T data;

    public static <T> AiCommonResult<T> ok(T data) {
        AiCommonResult<T> r = new AiCommonResult<>();
        r.code = 0;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> AiCommonResult<T> fail(String msg) {
        AiCommonResult<T> r = new AiCommonResult<>();
        r.code = 1;
        r.msg = msg;
        return r;
    }
}
