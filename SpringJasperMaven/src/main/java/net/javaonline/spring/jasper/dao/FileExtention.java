package net.javaonline.spring.jasper.dao;

/**
 * Created by UmarHussain on 10/12/2016.
 */
public enum FileExtention {


    XlSX("xlsx","xlsx"),CSV("csv","csv"),XLS("xls","xls"),PDF("pdf","pdf"),HTML("html","html");

    private final String id;
    private final String value;

    FileExtention(String id,String value){

        this.id = id;
        this.value = value;

    }

    public String getId(){return id;}
    public String getValue(){return value;}

}
