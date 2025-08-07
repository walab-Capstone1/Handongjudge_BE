package com.project.handongjudge.problem.util;

import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
@Component
@Getter
@Setter
public class CodeExtenstion {

    public static String getCodeExtension(String language) {
        switch (language) {
            case "python":
                return "py";
            case "java":
                return "java";
            case "cpp":
                return "cpp";
            case "c":
                return "c";
            case "javascript":
                return "js";
            default:
                return "txt";
        }
    }
    
    public static File StringToFile(String language, String code){
        // use getCodeExtenstion  get C
        // make file name 
        // make file
        // return file
        String extension = language.toLowerCase();
        String fileName = "code." + extension;
        File file = new File(fileName);
        try {
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(code);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }
        
}
