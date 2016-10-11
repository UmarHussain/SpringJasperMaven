package net.javaonline.spring.jasper.dao;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import de.cenote.jasperstarter.Config;
import net.javaonline.spring.jasper.form.JasperInputForm;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.*;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


public class JasperReportDAO {


    public Connection getConnection() throws SQLException {
        Connection conn = null;

        try {

            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.out.println("Please include Classpath Where your MySQL Driver is located");
            e.printStackTrace();
        }

        conn = DriverManager.getConnection("jdbc:mysql://localhost:3307/springjasperdemo", "root", "root");


        if (conn != null) {
            System.out.println("Database Connected");
        } else {
            System.out.println(" connection Failed ");
        }


        return conn;


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


    public void generateReportHtml(JasperPrint jasperPrint, HttpServletRequest req,
                                   HttpServletResponse resp) throws IOException, JRException {
        HtmlExporter exporter = new HtmlExporter();
        List<JasperPrint> jasperPrintList = new ArrayList<JasperPrint>();
        jasperPrintList.add(jasperPrint);
        exporter.setExporterInput(SimpleExporterInput.getInstance(jasperPrintList));
        exporter.setExporterOutput(new SimpleHtmlExporterOutput(resp.getWriter()));
        SimpleHtmlReportConfiguration configuration = new SimpleHtmlReportConfiguration();
        exporter.setConfiguration(configuration);
        exporter.exportReport();

    }


    //webExportCsv
    public void generateReportCsv(JasperPrint jasperPrint, HttpServletResponse response, String type) throws IOException, JRException {

        JRCsvExporter exporter = new JRCsvExporter();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
        exporter.setParameter(JRCsvExporterParameter.FIELD_DELIMITER, ";");
        exporter.setParameter(JRCsvExporterParameter.RECORD_DELIMITER, "\r\n");
        exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, baos);
        exporter.exportReport();

        byte[] download = baos.toByteArray();
        String ext = type;
        /*if(StringUtils.startsWith(ext, "xls"))
            ext = "xls";*/
        String fileName = "EmployeeMaster" + "-" + System.currentTimeMillis() + "." + ext;

        response.setContentType("application/octet-stream");
        response.setContentLength(download.length);
        String contentDisposition = "attachment; filename=\"" + fileName + "\"";
        response.setHeader("Content-Disposition", contentDisposition);

        response.getOutputStream().write(download);

        baos.flush();
        baos.close();

    }

    public void generateReportCsv2(JasperPrint jasperPrint, HttpServletResponse response, String type) throws IOException, JRException {
        JRCsvExporter exporter = new JRCsvExporter();

        OutputStream outputStream = response.getOutputStream();

        exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
        exporter.setParameter(JRCsvExporterParameter.FIELD_DELIMITER, ";");
        exporter.setParameter(JRCsvExporterParameter.RECORD_DELIMITER, "\r\n");
        String ext = type;
        String fileName = "EmployeeMaster" + "-" + System.currentTimeMillis() + "." + ext;
        String contentDisposition = "attachment; filename=\"" + fileName + "\"";
        response.setHeader("Content-Disposition", "attachment;filename=testcsv.csv");
        StringBuffer buffer = new StringBuffer();
        exporter.setParameter(JRExporterParameter.OUTPUT_STRING_BUFFER, buffer);

        exporter.exportReport();

        outputStream.write(buffer.toString().getBytes());
        response.flushBuffer();
    }


    public void generateReportPDF(HttpServletResponse resp, Map parameters,
                                  JasperReport jasperReport, Connection conn)
            throws JRException, NamingException, SQLException, IOException {
        byte[] bytes = null;
        bytes = JasperRunManager.runReportToPdf(jasperReport, parameters, conn);
        resp.reset();
        resp.resetBuffer();
        resp.setContentType("application/pdf");
        resp.setContentLength(bytes.length);
        ServletOutputStream ouputStream = resp.getOutputStream();
        ouputStream.write(bytes, 0, bytes.length);
        ouputStream.flush();
        ouputStream.close();
    }


}