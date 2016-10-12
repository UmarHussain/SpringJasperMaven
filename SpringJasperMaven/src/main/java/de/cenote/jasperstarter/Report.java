/*
 * Copyright 2012-2015 Cenote GmbH.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cenote.jasperstarter;

import de.cenote.jasperstarter.gui.ParameterPrompt;
import de.cenote.jasperstarter.types.DsType;
import de.cenote.jasperstarter.types.InputType;
import de.cenote.jasperstarter.types.OutputFormat;
import de.cenote.tools.printing.Printerlookup;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRCsvDataSource;
import net.sf.jasperreports.engine.data.JRXmlDataSource;
import net.sf.jasperreports.engine.data.JsonDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.*;
import net.sf.jasperreports.engine.export.oasis.JROdsExporter;
import net.sf.jasperreports.engine.export.oasis.JROdtExporter;
import net.sf.jasperreports.engine.export.ooxml.JRDocxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRPptxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.util.JRSaver;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.*;
import net.sf.jasperreports.view.JasperViewer;
import org.apache.commons.lang.LocaleUtils;

import javax.print.PrintService;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.HashPrintServiceAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.Copies;
import javax.servlet.http.HttpServletResponse;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Volker Voßkämper <vvo at cenote.de>
 * @version $Revision: 5b92831f1a80:54 branch:default $
 */
public class Report {

        private Config config;
        private File inputFile;
        private InputType initialInputType;
        private JasperDesign jasperDesign;
        private JasperReport jasperReport;
        private JasperPrint jasperPrint;
        private File output;
        private Locale defaultLocale;

        /**
         *
         * @param inputFile
         * @throws IllegalArgumentException
         */
        Report(Config config, File inputFile) throws IllegalArgumentException {
                this.config = config;
                // store the given default to reset to it in fill()
                defaultLocale = Locale.getDefault();

                this.inputFile = inputFile;

                Object inputObject = null;
                // Load the input file and try to evaluate the filetype
                // this fails in case of an jrxml file
                try {
                        inputObject = JRLoader.loadObject(inputFile);
                        Boolean casterror = true;
                        try {
                                jasperReport = (JasperReport) inputObject;
                                casterror = false;
                                initialInputType = InputType.JASPER_REPORT;
                        } catch (ClassCastException ex) {
                                // nothing to do here
                        }
                        try {
                                jasperPrint = (JasperPrint) inputObject;
                                casterror = false;
                                initialInputType = InputType.JASPER_PRINT;
                        } catch (ClassCastException ex) {
                                // nothing to do here
                        }
                        if (casterror) {
                                throw new IllegalArgumentException("input file: \"" + inputFile + "\" is not of a valid type");
                        }
                } catch (JRException ex) {
                        try {
                                // now try to load it as jrxml
                                jasperDesign = JRXmlLoader.load(inputFile.getAbsolutePath());
                                initialInputType = InputType.JASPER_DESIGN;
                                compile();
                        } catch (JRException ex1) {
                                throw new IllegalArgumentException("input file: \"" + inputFile + "\" is not a valid jrxml file", ex1);
                        }
                }

                // generating output basename
                // get the basename of inputfile
                String inputBasename = inputFile.getName().split("\\.(?=[^\\.]+$)")[0];
                if (!config.hasOutput()) {
                        // if output is omitted, use parent dir of input file
                        File parent = inputFile.getParentFile();
                        if (parent != null) {
                                this.output = parent;
                        } else {
                                this.output = new File(inputBasename);
                        }
                } else {
                        this.output = new File(config.getOutput()).getAbsoluteFile();
                }
                if (this.output.isDirectory()) {
                        // if output is an existing directory, add the basename of input
                        this.output = new File(this.output, inputBasename);
                }
                if (config.isVerbose()) {
                        System.out.println("Input absolute :  " + inputFile.getAbsolutePath());
                        try {
                                System.out.println("Input canonical:  " + inputFile.getCanonicalPath());
                        } catch (IOException ex) {
                                Logger.getLogger(Report.class.getName()).log(Level.SEVERE, null, ex);
                        }
                        System.out.println("Input:            " + inputFile.getName());
                        System.out.println("Input basename:   " + inputBasename);
                        if (config.hasOutput()) {
                                File outputParam = new File(config.getOutput()).getAbsoluteFile();
                                System.out.println("OutputParam:      " + outputParam.getAbsolutePath());
                        }
                        System.out.println("Output:           " + output.getAbsolutePath());
                        try {
                                System.out.println("Output canonical: " + output.getCanonicalPath());
                        } catch (IOException ex) {
                                Logger.getLogger(Report.class.getName()).log(Level.SEVERE, null, ex);
                        }
                }
        }

        private void compile() throws JRException {
                jasperReport = JasperCompileManager.compileReport(jasperDesign);
                // this option is only available if command process is active
                if (config.isWriteJasper()) {
                        String inputBasename = inputFile.getName().split("\\.(?=[^\\.]+$)")[0];
                        String outputDir = inputFile.getParent();
                        File outputFile = new File(outputDir + "/" + inputBasename + ".jasper");
                        JRSaver.saveObject(jasperReport, outputFile);
                }
        }

        public void compileToFile() {
                if (initialInputType == InputType.JASPER_DESIGN) {
                        try {
                                JRSaver.saveObject(jasperReport, this.output.getAbsolutePath() + ".jasper");
                        } catch (JRException ex) {
                                throw new IllegalArgumentException("outputFile" + this.output.getAbsolutePath() + ".jasper" + "could not be written");
                        }
                } else {
                        throw new IllegalArgumentException("input file: \"" + inputFile + "\" is not a valid jrxml file");
                }
        }

        public void fill() throws InterruptedException {
                if (initialInputType != InputType.JASPER_PRINT) {
                        // get commandLineReportParams
                        Map<String, Object> parameters = getCmdLineReportParams();
                        // if prompt...
                        if (config.hasAskFilter()) {
                                JRParameter[] reportParams = jasperReport.getParameters();
                                parameters = promptForParams(reportParams, parameters, jasperReport.getName());
                        }
                        try {
                                if (parameters.containsKey("REPORT_LOCALE")) {
                                        if (parameters.get("REPORT_LOCALE") != null) {
                                                Locale.setDefault((Locale) parameters.get("REPORT_LOCALE"));
                                        }
                                }
                                if (DsType.none.equals(config.getDbType())) {
                                        jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
                                } else if (DsType.csv.equals(config.getDbType())) {
                                        Db db = new Db();
                                        JRCsvDataSource ds = db.getCsvDataSource(config);
                                        jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, ds);
                                } else if (DsType.xml.equals(config.getDbType())) {
                                        Db db = new Db();
                                        JRXmlDataSource ds = db.getXmlDataSource(config);
                                        jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, ds);
                                } else if (DsType.json.equals(config.getDbType())) {
                                        Db db = new Db();
                                        JsonDataSource ds = db.getJsonDataSource(config);
                                        jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, ds);
                                } else {
                                        Db db = new Db();
                                        Connection con = db.getConnection(config);
                                        jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, con);
                                        con.close();
                                }
                                // reset to default
                                Locale.setDefault(defaultLocale);
                        } catch (SQLException ex) {
                                throw new IllegalArgumentException("Unable to connect to database: " + ex.getMessage(), ex);
                        } catch (JRException e) {
                                throw new IllegalArgumentException("Error filling report" + e.getMessage(), e);
                        } catch (ClassNotFoundException e) {
                                throw new IllegalArgumentException("Unable to load driver: " + e.getMessage(), e);
                        } finally {
                                // reset to default
                                Locale.setDefault(defaultLocale);
                        }
                        List<OutputFormat> formats = config.getOutputFormats();
                        try {
                                if (formats.contains(OutputFormat.jrprint)) {
                                        JRSaver.saveObject(jasperPrint, this.output.getAbsolutePath() + ".jrprint");
                                }
                        } catch (JRException e) {
                                throw new IllegalArgumentException("Unable to write to file: " + this.output.getAbsolutePath() + ".jrprint", e);
                        }
                }
        }

        public void print() throws JRException {
                PrintRequestAttributeSet printRequestAttributeSet = new HashPrintRequestAttributeSet();
                //printRequestAttributeSet.add(MediaSizeName.ISO_A4);
                if (config.hasCopies()){
                        printRequestAttributeSet.add(new Copies(config.getCopies().intValue()));
                }
                PrintServiceAttributeSet printServiceAttributeSet = new HashPrintServiceAttributeSet();
                //printServiceAttributeSet.add(new PrinterName("Fax", null));
                JRPrintServiceExporter exporter = new JRPrintServiceExporter();
                if (config.hasReportName()) {
                        jasperPrint.setName(config.getReportName());
                }
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                SimplePrintServiceExporterConfiguration expConfig = new SimplePrintServiceExporterConfiguration();
                if (config.hasPrinterName()) {
                        String printerName = config.getPrinterName();
                        PrintService service = Printerlookup.getPrintservice(printerName, Boolean.TRUE, Boolean.TRUE);
                        expConfig.setPrintService(service);
                        if (config.isVerbose()) {
                                System.out.println(
                                        "printer-name: " + ((service == null)
                                                ? "No printer found with name \""
                                                + printerName + "\"! Using default." : "found: "
                                                + service.getName()));
                        }
                }
                //exporter.setParameter(JRExporterParameter.PAGE_INDEX, pageIndex);
                //exporter.setParameter(JRExporterParameter.START_PAGE_INDEX, pageStartIndex);
                //exporter.setParameter(JRExporterParameter.END_PAGE_INDEX, pageEndIndex);
                expConfig.setPrintRequestAttributeSet(printRequestAttributeSet);
                expConfig.setPrintServiceAttributeSet(printServiceAttributeSet);
                expConfig.setDisplayPageDialog(Boolean.FALSE);
                if (config.isWithPrintDialog()) {
                        setLookAndFeel();
                        expConfig.setDisplayPrintDialog(Boolean.TRUE);
                } else {
                        expConfig.setDisplayPrintDialog(Boolean.FALSE);
                }
                exporter.setConfiguration(expConfig);
                exporter.exportReport();
        }

        public void view() throws JRException {
                setLookAndFeel();
                JasperViewer.viewReport(jasperPrint, false);
        }

        public void exportPdf() throws JRException {
                JasperExportManager.exportReportToPdfFile(jasperPrint,
                        this.output.getAbsolutePath() + ".pdf");
        }

        public void exportRtf() throws JRException {
                JRRtfExporter exporter = new JRRtfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(this.output
                        .getAbsolutePath() + ".rtf"));
                exporter.exportReport();
        }

        public void exportDocx() throws JRException {
                JRDocxExporter exporter = new JRDocxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(this.output
                        .getAbsolutePath() + ".docx"));
                exporter.exportReport();
        }

        public void exportOdt() throws JRException {
                JROdtExporter exporter = new JROdtExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(this.output
                        .getAbsolutePath() + ".odt"));
                exporter.exportReport();
        }

        public void exportHtml() throws JRException {
                JasperExportManager.exportReportToHtmlFile(jasperPrint,
                        this.output.getAbsolutePath() + ".html");
        }

        public void exportXml() throws JRException {
                JasperExportManager.exportReportToXmlFile(jasperPrint,
                        this.output.getAbsolutePath() + ".xml", false);
        }

        public void exportXls() throws JRException {
                Map<String, String> dateFormats = new HashMap<String, String>();
                dateFormats.put("EEE, MMM d, yyyy", "ddd, mmm d, yyyy");

                JRXlsExporter exporter = new JRXlsExporter();
                SimpleXlsReportConfiguration repConfig = new SimpleXlsReportConfiguration();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(
                        this.output.getAbsolutePath() + ".xls"));
                repConfig.setDetectCellType(Boolean.TRUE);
                repConfig.setFormatPatternsMap(dateFormats);
                exporter.setConfiguration(repConfig);
                exporter.exportReport();
        }

        // the XLS Metadata Exporter
        public void exportXlsMeta() throws JRException {
                Map<String, String> dateFormats = new HashMap<String, String>();
                dateFormats.put("EEE, MMM d, yyyy", "ddd, mmm d, yyyy");

                JRXlsMetadataExporter exporter = new JRXlsMetadataExporter();
                SimpleXlsMetadataReportConfiguration repConfig = new SimpleXlsMetadataReportConfiguration();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(
                        this.output.getAbsolutePath() + ".xls"));
                repConfig.setDetectCellType(Boolean.TRUE);
                repConfig.setFormatPatternsMap(dateFormats);
                exporter.setConfiguration(repConfig);
                exporter.exportReport();
        }

        public void exportXlsx() throws JRException {
                Map<String, String> dateFormats = new HashMap<String, String>();
                dateFormats.put("EEE, MMM d, yyyy", "ddd, mmm d, yyyy");

                JRXlsxExporter exporter = new JRXlsxExporter();
                SimpleXlsxReportConfiguration repConfig = new SimpleXlsxReportConfiguration();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(
                        this.output.getAbsolutePath() + ".xlsx"));
                repConfig.setDetectCellType(Boolean.TRUE);
                repConfig.setFormatPatternsMap(dateFormats);
                exporter.setConfiguration(repConfig);
                exporter.exportReport();
        }




        public void exportCsv() throws JRException {
                JRCsvExporter exporter = new JRCsvExporter();
                SimpleCsvExporterConfiguration configuration = new SimpleCsvExporterConfiguration();
                configuration.setFieldDelimiter(config.getOutFieldDel());
                exporter.setConfiguration(configuration);
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(
                        this.output.getAbsolutePath() + ".csv", config.getOutCharset()));
                exporter.exportReport();
        }


        // the CSV Metadata Exporter
        public void exportCsvMeta() throws JRException {
                JRCsvMetadataExporter exporter = new JRCsvMetadataExporter();
                SimpleCsvMetadataExporterConfiguration configuration = new SimpleCsvMetadataExporterConfiguration();
                configuration.setFieldDelimiter(config.getOutFieldDel());
                exporter.setConfiguration(configuration);
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleWriterExporterOutput(
                        this.output.getAbsolutePath() + ".csv", config.getOutCharset()));
                exporter.exportReport();
        }

        public void exportOds() throws JRException {
                JROdsExporter exporter = new JROdsExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(
                        this.output.getAbsolutePath() + ".ods"));
                exporter.exportReport();
        }

        public void exportPptx() throws JRException {
                JRPptxExporter exporter = new JRPptxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(
                        this.output.getAbsolutePath() + ".pptx"));
                exporter.exportReport();
        }

        public void exportXhtml() throws JRException {
                HtmlExporter exporter = new HtmlExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleHtmlExporterOutput(this.output
                        .getAbsolutePath() + ".x.html"));
                exporter.exportReport();
        }

        private Map<String, Object> getCmdLineReportParams() {
                JRParameter[] jrParameterArray = jasperReport.getParameters();
                Map<String, JRParameter> jrParameters = new HashMap<String, JRParameter>();
                Map<String, Object> parameters = new HashMap<String, Object>();
                List<String> params;
                if (config.hasParams()) {
                        params = config.getParams();
                        for (JRParameter rp : jrParameterArray) {
                                jrParameters.put(rp.getName(), rp);
                        }
                        String paramName = null;
                        //String paramType = null;
                        String paramValue = null;

                        for (String p : params) {
                                try {
                                        paramName = p.split("=")[0];
                                        paramValue = p.split("=", 2)[1];
                                        if (config.isVerbose()) {
                                                System.out.println("Using report parameter: "
                                                        + paramName + " = " + paramValue);
                                        }
                                } catch (Exception e) {
                                        throw new IllegalArgumentException("Wrong report param format! " + p, e);
                                }
                                if (!jrParameters.containsKey(paramName)) {
                                        throw new IllegalArgumentException("Parameter '"
                                                + paramName + "' does not exist in report!");
                                }

                                JRParameter reportParam = jrParameters.get(paramName);

                                try {
                                        // special parameter handlers must also implemeted in
                                        // ParameterPanel.java
                                        if (Date.class
                                                .equals(reportParam.getValueClass())) {
                                                // Date must be in ISO8601 format. Example 2012-12-31
                                                DateFormat dateFormat = new SimpleDateFormat("yy-MM-dd");
                                                parameters.put(paramName, (Date) dateFormat.parse(paramValue));
                                        } else if (Image.class
                                                .equals(reportParam.getValueClass())) {
                                                Image image =
                                                        Toolkit.getDefaultToolkit().createImage(
                                                                JRLoader.loadBytes(new File(paramValue)));
                                                MediaTracker traker = new MediaTracker(new Panel());
                                                traker.addImage(image, 0);
                                                try {
                                                        traker.waitForID(0);
                                                } catch (Exception e) {
                                                        throw new IllegalArgumentException(
                                                                "Image tracker error: " + e.getMessage(), e);
                                                }
                                                parameters.put(paramName, image);
                                        } else if (Locale.class
                                                .equals(reportParam.getValueClass())) {
                                                parameters.put(paramName, LocaleUtils.toLocale(paramValue));
                                        } else {
                                                // handle generic parameters with string constructor
                                                try {
                                                        parameters.put(paramName,
                                                                reportParam.getValueClass()
                                                                        .getConstructor(String.class)
                                                                        .newInstance(paramValue));
                                                } catch (InstantiationException ex) {
                                                        throw new IllegalArgumentException("Parameter '"
                                                                + paramName + "' of type '"
                                                                + reportParam.getValueClass().getName()
                                                                + " caused " + ex.getClass().getName()
                                                                + " " + ex.getMessage(), ex);
                                                } catch (IllegalAccessException ex) {
                                                        throw new IllegalArgumentException("Parameter '"
                                                                + paramName + "' of type '"
                                                                + reportParam.getValueClass().getName()
                                                                + " caused " + ex.getClass().getName()
                                                                + " " + ex.getMessage(), ex);
                                                } catch (InvocationTargetException ex) {
                                                        Throwable cause = ex.getCause();
                                                        throw new IllegalArgumentException("Parameter '"
                                                                + paramName + "' of type '"
                                                                + reportParam.getValueClass().getName()
                                                                + " caused " + cause.getClass().getName()
                                                                + " " + cause.getMessage(), cause);
                                                } catch (NoSuchMethodException ex) {
                                                        throw new IllegalArgumentException("Parameter '"
                                                                + paramName + "' of type '"
                                                                + reportParam.getValueClass().getName()
                                                                + " with value '" + paramValue
                                                                + "' is not supported by JasperStarter!", ex);
                                                }
                                        }
                                } catch (NumberFormatException e) {
                                        throw new IllegalArgumentException("NumberFormatException: " + e.getMessage() + "\" in \"" + p + "\"", e);
                                } catch (java.text.ParseException e) {
                                        throw new IllegalArgumentException(e.getMessage() + "\" in \"" + p + "\"", e);
                                } catch (JRException e) {
                                        throw new IllegalArgumentException("Unable to load image from: " + paramValue, e);
                                }
                        }
                }
                return parameters;
        }

        public static void setLookAndFeel() {
                try {
                        // Set System L&F
                        UIManager.setLookAndFeel(
                                UIManager.getSystemLookAndFeelClassName());
                } catch (UnsupportedLookAndFeelException e) {
                        e.printStackTrace();
                } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                } catch (InstantiationException e) {
                        e.printStackTrace();
                } catch (IllegalAccessException e) {
                        e.printStackTrace();
                }
        }

        private Map<String, Object> promptForParams(JRParameter[] reportParams, Map<String, Object> params, String reportName) throws InterruptedException {
                boolean isForPromptingOnly = false;
                boolean isUserDefinedOnly = false;
                boolean emptyOnly = false;
                switch (config.getAskFilter()) {
                        case ae:
                                emptyOnly = true;
                        case a:
                                isForPromptingOnly = false;
                                isUserDefinedOnly = false;
                                break;
                        case ue:
                                emptyOnly = true;
                        case u:
                                isUserDefinedOnly = true;
                                break;
                        case pe:
                                emptyOnly = true;
                        case p:
                                isUserDefinedOnly = true;
                                isForPromptingOnly = true;
                                break;
                }
                Report.setLookAndFeel();
                ParameterPrompt prompt = new ParameterPrompt(null, reportParams, params,
                        reportName, isForPromptingOnly, isUserDefinedOnly, emptyOnly);
                if (JOptionPane.OK_OPTION != prompt.show()) {
                        throw new InterruptedException("User aborted at parameter promt!");
                }
                if (config.isVerbose()) {
                        System.out.println("----------------------------");
                        System.out.println("Parameter prompt:");
                        for (Object key : params.keySet()) {
                                System.out.println(key + " = " + params.get(key));
                        }
                        System.out.println("----------------------------");
                }
                return params;
        }

        public JRParameter[] getReportParameters() throws IllegalArgumentException {
                JRParameter[] returnval = null;
                if (jasperReport != null) {
                        returnval = jasperReport.getParameters();
                } else {
                        throw new IllegalArgumentException(
                                "Parameters could not be read from "
                                        + inputFile.getAbsolutePath());
                }
                return returnval;
        }
}
