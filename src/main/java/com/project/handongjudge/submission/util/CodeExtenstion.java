package com.project.handongjudge.submission.util;

import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException; 
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
@Getter
@Setter
public class CodeExtenstion {

    private static final Logger log = LoggerFactory.getLogger(CodeExtenstion.class);
    private static final String TEMP_DIR = "./tmp/submissions";

    public static String getCodeExtension(String language) {
        switch (language) {
            case "python":
                return ".py";
            case "java":
                return ".java";
            case "cpp":
                return ".cpp";
            case "c":
                return ".c";
            case "javascript":
                return ".js";
            default:
                return ".txt";
        }
    }
    
    public static File StringToFile(String language, String code){
        // use getCodeExtenstion  get C
        // make file name 
        // make file
        // return file
        String extension = getCodeExtension(language);
        String fileName = UUID.randomUUID().toString() + extension; 
        log.debug("파일 이름: {}", fileName);
        Path tempFile = Paths.get(TEMP_DIR, fileName);

        try {
            Files.createDirectories(tempFile.getParent());
            Files.write(tempFile, code.getBytes(StandardCharsets.UTF_8));
            System.out.println("파일 저장 성공: " + tempFile);
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }


        return tempFile.toFile();
    }

    public static File multipartToFile(MultipartFile multipartFile, String language) {
        try {
            File tempFile = File.createTempFile("submission_", "." + language);
            multipartFile.transferTo(tempFile);
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("파일 저장 실패", e);
        }
    }
}
