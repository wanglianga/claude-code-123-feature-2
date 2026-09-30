package cn.court.mediation.service;

import cn.court.mediation.common.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** 履行凭证/异议材料等附件的本地存储（演示环境） */
@Component
public class AttachmentStorage {

    private final Path root;

    public AttachmentStorage(@Value("${app.upload.dir:./data/attachments}") String uploadDir) {
        this.root = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建附件存储目录：" + this.root, e);
        }
    }

    /** 保存上传文件，返回 [原始文件名, 相对存储路径] */
    public String[] store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("附件为空");
        }
        String original = StringUtils.cleanPath(
                file.getOriginalFilename() == null ? "附件" : file.getOriginalFilename());
        String ext = "";
        int dot = original.lastIndexOf('.');
        if (dot >= 0) ext = original.substring(dot);
        String stored = UUID.randomUUID().toString().replace("-", "") + ext;
        try {
            Path target = root.resolve(stored).normalize();
            if (!target.startsWith(root)) {
                throw new BizException("非法存储路径");
            }
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException("附件保存失败：" + e.getMessage());
        }
        return new String[]{original, stored};
    }

    public Path resolve(String storedPath) {
        Path p = root.resolve(storedPath).normalize();
        if (!p.startsWith(root) || !Files.exists(p)) {
            throw new BizException("附件不存在或已删除");
        }
        return p;
    }
}
