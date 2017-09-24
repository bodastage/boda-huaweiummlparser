package com.bodastage.huawei_umml_parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Huawei UMML Parser 
 * 
 * @author Emmanue Robert Ssebaggala
 * @version 1.0.0
 */
public class HuaweiUMMLParser {
    
    /**
     * This holds a map of the Managed Object Instances (MOIs) to the respective
     * csv print writers.
     * 
     * @since 1.0.0
     */
    private Map<String, PrintWriter> moiPrintWriters 
            = new LinkedHashMap<String, PrintWriter>();
    
    /**
     * Manage Object Name
     * 
     * @since 1.0.0
     */
    private String moName = null;
    
    /**
     * Network element name
     * 
     * @since 1.0.0
     */
    private String NE; 
    
    /**
     * Export date
     * 
     * @since 1.0.0
     */
    private String dateTime;
    
    /**
     * Parser start time. 
     * 
     * @since 1.0.4
     * @version 1.0.0
     */
    final long startTime = System.currentTimeMillis();
    
    /**
     * Output directory.
     *
     * @since 1.0.0
     */
    private String outputDirectory;
    
    /**
     * Mark start of data section
     * 
     * @since 1.0.0
     */
    private boolean startOfDataSection;

    /**
     * A map of the MO and it's attributes in the file 
     * 
     * @since 1.0.0
     */
    private Map<String,Stack> moNameAttrsMap 
            = new LinkedHashMap<String, Stack>();
   
    /**
     * The base file name of the file being parsed.
     * 
     * @since 1.0.0
     */
    private String baseFileName = "";
    
    /**
     * A map of the MO and it's attributes in the file 
     * 
     * @since 1.0.0
     */
    private Map<String,Stack> moNameStartIndexMap 
            = new LinkedHashMap<String, Stack>();
   
    /**
     * Parser states. Currently there are only 2: extraction and parsing
     * 
     * @since 1.0.0
     */
    private int parserState = ParserStates.EXTRACTING_VALUES;
    
    
    /**
     * The file or directory containing the MML files 
     * 
     * @since 1.0.0
     */
    private String dataSource;
    
    
    /**
     * Command execution return code 
     * 
     * @since 1.0.0
     */
    private int returnCode = -1;
    
    /**
     * Mark whether the header has been processed
     * 
     * @since 1.0.0
     */
    private boolean headerProcessed  = false;
    
    /**
     * The file to be parsed.
     * 
     * @since 1.0.0
     */
    private String dataFile;
    
    /**
     * Parse a single file 
     * 
     * @param fileName 
     */
    public void parseFile( String fileName) throws FileNotFoundException, IOException{
            BufferedReader br = new BufferedReader(new FileReader(fileName));
            for(String line; (line = br.readLine()) != null; ) {
                processLine(line);
            }
    }
    
    /**
     * Process line by line 
     * 
     * @param line 
     */
    public void processLine (String line ) throws FileNotFoundException{
        
        
        if(line.equals("")){ return; }
        

        //Mark end of data section
        if(line.startsWith("(Number of results")){ 
            this.startOfDataSection = false;
            returnCode = -1;
            headerProcessed = false;
            moNameAttrsMap.remove(moName);
            moNameStartIndexMap.remove(moName);
            return; 
        }
        
        //Extact MO name
        //LST <MONAME>:;
        if(line.startsWith("MML Command")){
            Pattern p = Pattern.compile("LST ([^:]+):");
            Matcher matcher = p.matcher(line);
            matcher.find();
            this.moName = matcher.group(1);
            
            return;
        }
        
        //Extract the NE
        //NE : XXXX
        if(line.startsWith("NE :")){
            String [] sArray = line.split(":");
            this.NE = sArray[1].trim();
            return;
        }
        
        //Extract the date
        //Report : ... 
        if(line.startsWith("Report :")){
            Pattern p = Pattern.compile("\\s+([^\\s]+\\s[^\\s]+)$");
            Matcher matcher = p.matcher(line);
            matcher.find();
            this.dateTime = matcher.group(1);
            
            return;
        }
        
        //Get return code 
        if(line.startsWith("RETCODE = 0  Execution succeeded.")){
            this.returnCode = 0;
            return;
        }
        
        //Check if this is the start of --------------
        //returnCode should 0
        //
        if(line.startsWith("-----") && returnCode == 0){
            this.startOfDataSection = true;
            return;
        }
        
        //Process header section/\
        if(startOfDataSection == true && headerProcessed == false){
            headerProcessed = true;
            
            //Check if the parameters have already been added
            if( moNameAttrsMap.containsKey(moName)){
                return;
            }

            
            Stack paramStack = new Stack(); //To hold the parameters
            Stack startIndexStack = new Stack(); //To hold the start indices for each parameter
            
            String [] headerStrings = line.trim().split("\\s{2,}");
            String fileHeader = "DateTime,NE";
            
            for(int i = 0; i < headerStrings.length; i ++){
                headerStrings[i] = headerStrings[i].trim();
                
                int startIndex = line.indexOf(headerStrings[i]);
                paramStack.push(headerStrings[i]);
                startIndexStack.push(startIndex);      
                fileHeader += "," + headerStrings[i];
            }
            
            moNameAttrsMap.put(moName, paramStack);
            moNameStartIndexMap.put(moName, startIndexStack);
            
            if(!moiPrintWriters.containsKey(moName)){
                String moiFile = outputDirectory + File.separatorChar + moName +  ".csv";
                moiPrintWriters.put(moName, new PrintWriter(moiFile));
                moiPrintWriters.get(moName).println(fileHeader);
            }
            return;
        }
        
        
        
        //System.out.println(line);
        //System.out.println("startOfDataSection:" + startOfDataSection );
        //Collect data
        if(startOfDataSection == true ){
            //System.out.println(line);
            String csvValues = dateTime + "," + NE;
            
            Stack parameters = moNameAttrsMap.get(moName);
            Stack paramStackIndices = moNameStartIndexMap.get(moName);
            
            int size = parameters.size();
            for(int i =0; i < size; i++){
                String parameter = (String)parameters.get(i);
                int startIndex = (int)paramStackIndices.get(i);
                
                int length = parameter.trim().length();
                
                String value ="";
                
                if( i == size -1){ //Handle the last parameter value
                    value = line.substring(startIndex);
                }else{
                    value = line.substring(startIndex, startIndex+length-1);
                }
                //String trimmed = trimAdvanced(value);
                
                csvValues += "," + toCSVFormat(value.trim());
            }

            moiPrintWriters.get(moName).println(csvValues);
            
        }
        
    }
    
     /**
     * Process given string into a format acceptable for CSV format.
     *
     * @since 1.0.0
     * @param s String
     * @return String Formated version of input string
     */
    public String toCSVFormat(String s) {
        String csvValue = s;

        //Strip start and end quotes
        s = s.replaceAll("^\"|\"$", "");
        
        //Check if value contains comma
        if (s.contains(",")) {
            csvValue = "\"" + s + "\"";
        }

        
        if (s.contains("\"")) {
            csvValue = "\"" + s.replace("\"", "\"\"") + "\"";
        }

        return csvValue;
    }
    
    public  static void main(String[] args){
        
        try{
            
            
            //show help
            if(args.length != 2 || (args.length == 1 && args[0] == "-h")){
                showHelp();
                System.exit(1);
            }
            //Get bulk CM XML file to parse.
            String filename = args[0];
            String outputDirectory = args[1];
            
            //Confirm that the output directory is a directory and has write 
            //privileges
            File fOutputDir = new File(outputDirectory);
            if(!fOutputDir.isDirectory()) {
                System.err.println("ERROR: The specified output directory is not a directory!.");
                System.exit(1);
            }
            
            if(!fOutputDir.canWrite()){
                System.err.println("ERROR: Cannot write to output directory!");
                System.exit(1);            
            }

            HuaweiUMMLParser parser = new HuaweiUMMLParser();
            parser.setDataSource(filename);
            parser.setOutputDirectory(outputDirectory);
            parser.parse();
            parser.printExecutionTime();
        }catch(Exception e){
            System.out.println(e.getMessage());
            System.exit(1);
        }
        
    }
    
    /**
     * Parser entry point 
     * 
     * @throws XMLStreamException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException 
     * 
     * @since 1.1.1
     */
    public void parse() throws IOException {
        //Extract parameters
        if (parserState == ParserStates.EXTRACTING_PARAMETERS) {
            processFileOrDirectory();

            parserState = ParserStates.EXTRACTING_VALUES;
        }

        //Extracting values
        if (parserState == ParserStates.EXTRACTING_VALUES) {
            processFileOrDirectory();
            parserState = ParserStates.EXTRACTING_DONE;
        }
        
        closeMOPWMap();
    }
    
    
    /**
     * Get file base name.
     * 
     * @since 1.0.0
     */
     public String getFileBasename(String filename){
        try{
            return new File(filename).getName();
        }catch(Exception e ){
            return filename;
        }
    }
     
    /**
     * Close file print writers.
     *
     * @since 1.0.0
     * @version 1.0.0
     */
    public void closeMOPWMap() {
        Iterator<Map.Entry<String, PrintWriter>> iter
                = moiPrintWriters.entrySet().iterator();
        while (iter.hasNext()) {
            iter.next().getValue().close();
        }
        moiPrintWriters.clear();
    }
    
    /**
     * Set name of file to parser.
     * 
     * @since 1.0.0
     * @param directoryName 
     */
    public void setFileName(String filename ){
        this.dataFile = filename;
    }
    
    /**
     * Determines if the source data file is a regular file or a directory and 
     * parses it accordingly
     * 
     * @since 1.0.0
     */
    public void processFileOrDirectory() throws IOException {
        //this.dataFILe;
        Path file = Paths.get(this.dataSource);
        boolean isRegularExecutableFile = Files.isRegularFile(file)
                & Files.isReadable(file);

        boolean isReadableDirectory = Files.isDirectory(file)
                & Files.isReadable(file);

        if (isRegularExecutableFile) {
            this.setFileName(this.dataSource);
            baseFileName =  getFileBasename(this.dataFile);
            
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                System.out.print("Extracting parameters from " + this.baseFileName + "...");
            }else{
                System.out.print("Parsing " + this.baseFileName + "...");
            }
                    
            this.parseFile(this.dataSource);
            
            if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                 System.out.println("Done.");
            }else{
                System.out.println("Done.");
                //System.out.println(this.baseFileName + " successfully parsed.\n");
            }
        }

        if (isReadableDirectory) {

            File directory = new File(this.dataSource);

            //get all the files from a directory
            File[] fList = directory.listFiles();

            for (File f : fList) {
                this.setFileName(f.getAbsolutePath());
                try {
                    baseFileName =  getFileBasename(this.dataFile);
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                        System.out.print("Extracting parameters from " + this.baseFileName + "...");
                    }else{
                        System.out.print("Parsing " + this.baseFileName + "...");
                    }
                    
                    //Parse
                    this.parseFile(f.getAbsolutePath());
                    if( parserState == ParserStates.EXTRACTING_PARAMETERS){
                         System.out.println("Done.");
                    }else{
                        System.out.println("Done.");
                        //System.out.println(this.baseFileName + " successfully parsed.\n");
                    }
                   
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                    System.out.println("Skipping file: " + this.baseFileName + "\n");
                }
            }
        }

    }


    /**
     * Set the output directory.
     * 
     * @since 1.0.0
     * @version 1.0.0
     * @param directoryName 
     */
    public void setOutputDirectory(String directoryName ){
        this.outputDirectory = directoryName;
    }
    
    /**
     * Set name of file to parser.
     * 
     * @since 1.0.0
     * @param dataSource 
     */
    public void setDataSource(String dataSource ){
        this.dataSource = dataSource;
    }
    
    /**
     * Print program's execution time.
     * 
     * @since 1.0.0
     */
    public void printExecutionTime(){
        float runningTime = System.currentTimeMillis() - startTime;
        
        String s = "Parsing completed. ";
        s = s + "Total time:";
        
        //Get hours
        if( runningTime > 1000*60*60 ){
            int hrs = (int) Math.floor(runningTime/(1000*60*60));
            s = s + hrs + " hours ";
            runningTime = runningTime - (hrs*1000*60*60);
        }
        
        //Get minutes
        if(runningTime > 1000*60){
            int mins = (int) Math.floor(runningTime/(1000*60));
            s = s + mins + " minutes ";
            runningTime = runningTime - (mins*1000*60);
        }
        
        //Get seconds
        if(runningTime > 1000){
            int secs = (int) Math.floor(runningTime/(1000));
            s = s + secs + " seconds ";
            runningTime = runningTime - (secs/1000);
        }
        
        //Get milliseconds
        if(runningTime > 0 ){
            int msecs = (int) Math.floor(runningTime/(1000));
            s = s + msecs + " milliseconds ";
            runningTime = runningTime - (msecs/1000);
        }

        
        System.out.println(s);
    }
    
    public String trimAdvanced(String value) {

        Objects.requireNonNull(value);

        int strLength = value.length();
        int len = value.length();
        int st = 0;
        char[] val = value.toCharArray();

        if (strLength == 0) {
            return "";
        }

        while ((st < len) && (val[st] <= ' ') || (val[st] == '\u00A0')) {
            st++;
            if (st == strLength) {
                break;
            }
        }
        while ((st < len) && (val[len - 1] <= ' ') || (val[len - 1] == '\u00A0')) {
            len--;
            if (len == 0) {
                break;
            }
        }


        return (st > len) ? "" : ((st > 0) || (len < strLength)) ? value.substring(st, len) : value;
    }    
    
    /**
     * Show parser help.
     * 
     * @since 1.0.0
     * @version 1.0.0
     */
    static public void showHelp(){
        System.out.println("boda-huaweiummlparser 1.0.0. Copyright (c) 2017 Bodastage(http://www.bodastage.com)");
        System.out.println("Parses Huawei MML printouts to csv.");
        System.out.println("Usage: java -jar boda-huaweiummlparser.jar <fileToParse}Directory> <outputDirectory>");
    }
}
