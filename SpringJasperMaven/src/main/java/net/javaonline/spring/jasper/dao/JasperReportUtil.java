package net.javaonline.spring.jasper.dao;

import de.cenote.jasperstarter.Config;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.export.JRCsvExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleCsvExporterConfiguration;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleWriterExporterOutput;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Created by Muhammad Umar Hussain on 10/12/2016.
 */
public class JasperReportUtil {


    JasperReportUtil(){}

    JasperReportUtil(String filename,HttpServletRequest request, HttpServletResponse response,String fileType)throws JRException{

        JasperReport report = getCompiledFile(filename,request);



    }



    public  void  exportCsv(Config config,JasperPrint jasperPrint,ByteArrayOutputStream os) throws JRException {
        JRCsvExporter exporter = new JRCsvExporter();
        SimpleCsvExporterConfiguration configuration = new SimpleCsvExporterConfiguration();
        configuration.setFieldDelimiter(config.getOutFieldDel());
        exporter.setConfiguration(configuration);
        exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
        exporter.setExporterOutput(new SimpleWriterExporterOutput(os));
        exporter.exportReport();
    }

    public JasperReport getCompiledFile(String fileName, HttpServletRequest request) throws JRException {
        System.out.println("path " + request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
        File reportFile = new File(request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
        // If compiled file is not found, then compile XML template
        if (!reportFile.exists()) {
            JasperCompileManager.compileReportToFile(request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jrxml"), request.getSession().getServletContext().getRealPath("/jasper/" + fileName + ".jasper"));
        }
        JasperReport jasperReport = (JasperReport) JRLoader.loadObjectFromFile(reportFile.getPath());
        return jasperReport;
    }

    public void webExport(HttpServletResponse response, String reportName, String fileExtention,JasperPrint jasperPrint) throws JRException,IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();



        if(fileExtention.equals(FileExtention.CSV)){
            exportCsv(new Config(),jasperPrint,baos);
        }

        byte[] download = baos.toByteArray();

        String fileName = reportName + "-" + System.currentTimeMillis() + "." + fileExtention;

        response.setContentType("application/octet-stream");
        response.setContentLength(download.length);
        String contentDisposition = "attachment; filename=\"" + fileName + "\"";
        response.setHeader("Content-Disposition", contentDisposition);

        response.getOutputStream().write(download);

        baos.flush();
        baos.close();




    }
}
