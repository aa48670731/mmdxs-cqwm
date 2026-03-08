package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    /**
     * 文件上传
     *
     * @param file
     * @return
     */
    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传,{}", file);
        try {
            //传入参数分别为字节数组(通过getBytes()方法获取)和上传到OSS的文件名(UUID)
            //仅有uuid不够，需要获取原始文件名的后缀名进行拼接(不然上传的文件就没有后缀名而识别不了是jpg文件了！)
            //获取原文件名
            String originalFilename = file.getOriginalFilename();
            //获取后缀名
            String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
            //进行uuid和后缀名的拼接
            String objectName = UUID.randomUUID().toString() + suffix;
            //填入参数,获取返回回来的图片网络地址
            String url = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(url);
        } catch (IOException e) {
            log.error("文件上传失败: {}", e);
        }
        //上传失败返回失败信息
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }

}
