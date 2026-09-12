package cn.nightpharmacy.web;

import cn.nightpharmacy.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/** 处方图片 / 冷链温控照片上传。 */
@RestController
@RequestMapping("/api/files")
public class FileController extends BaseController {

    @Value("${app.storage-dir}")
    private String storageDir;

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request)
            throws IOException {
        User u = currentUser(request);
        if (file.isEmpty()) throw new ApiException("上传文件为空");
        String dir = storageDir + "/" + LocalDate.now();
        Files.createDirectories(Paths.get(dir));
        String original = file.getOriginalFilename() == null ? "img.jpg" : file.getOriginalFilename();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : ".jpg";
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = Paths.get(dir, name);
        file.transferTo(target.toFile());
        String url = "/files/" + LocalDate.now() + "/" + name;
        return Map.of("success", true, "path", url, "url", url, "uploadedBy", u.getDisplayName());
    }
}
