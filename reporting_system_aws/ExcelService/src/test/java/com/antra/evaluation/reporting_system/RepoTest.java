package com.antra.evaluation.reporting_system;

import com.antra.evaluation.reporting_system.pojo.report.ExcelFile;
import com.antra.evaluation.reporting_system.repo.ExcelRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

public class RepoTest {
    @Autowired
    ExcelRepository excelRepository;
    Logger log = LoggerFactory.getLogger("Test");

    @Test
    public void testSearch() {
        Optional<ExcelFile> res = excelRepository.getFileById("1");
        System.out.println(res);
    }

    @Test
    public void testGetFile(){
        List<ExcelFile> res = excelRepository.getFiles();
        log.debug("ExcelFile Generated Size:"+res.size());

    }
}
