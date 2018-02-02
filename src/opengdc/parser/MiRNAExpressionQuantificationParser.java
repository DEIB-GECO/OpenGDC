/*
 * Application: OpenGDC
 * Version: 1.0
 * Authors: Fabio Cumbo (1,2), Eleonora Cappelli (1,2), Emanuel Weitschek (1,3)
 * Organizations: 
 * 1. Institute for Systems Analysis and Computer Science "Antonio Ruberti" - National Research Council of Italy, Rome, Italy
 * 2. Department of Engineering - Third University of Rome, Rome, Italy
 * 3. Department of Engineering - Uninettuno International University, Rome, Italy
 */
package opengdc.parser;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.GUI;
import opengdc.integration.GeneNames;
import opengdc.integration.MIRBase;
import opengdc.util.FSUtils;
import opengdc.util.FormatUtils;
import opengdc.util.GDCQuery;

/**
 *
 * @author fabio
 */
public class MiRNAExpressionQuantificationParser extends BioParser {

    @Override
    public int convert(String program, String disease, String dataType, String inPath, String outPath) {
        int acceptedFiles = FSUtils.acceptedFilesInFolder(inPath, getAcceptedInputFileFormats());
        System.err.println("Data Amount: " + acceptedFiles + " files" + "\n\n");
        GUI.appendLog(this.getLogger(), "Data Amount: " + acceptedFiles + " files" + "\n\n");
        
        if (acceptedFiles == 0)
            return 1;
        
        HashMap<File,File> error_inputFile2outputFile = new HashMap<>();
        HashSet<String> filesPathConverted = new HashSet<>();
        
        HashMap<String, HashMap<String, String>> mirnaid2coordinates = MIRBase.getMirnaid2coordinates();
        
        File[] files = (new File(inPath)).listFiles();
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension)) {
                    System.err.println("Processing " + f.getName());
                    GUI.appendLog(this.getLogger(), "Processing " + f.getName() + "\n");
                    
                    String file_uuid = f.getName().split("_")[0];
                    HashSet<String> attributes = new HashSet<>();
                    String aliquot_id_path = "cases.samples.portions.analytes.aliquots.aliquot_id";
                    attributes.add(aliquot_id_path);
                    HashMap<String, String> file_info = GDCQuery.retrieveExpInfoFromAttribute("files.file_id", file_uuid, attributes, 0, 0, null).get(0);
                    String aliquot_uuid = "";
                    if (file_info != null)
                        if (file_info.containsKey(aliquot_id_path))
                            aliquot_uuid = file_info.get(aliquot_id_path);
                    
                    if (!aliquot_uuid.trim().equals("")) {
                        String suffix_id = this.getOpenGDCSuffix(dataType, false);
                        String filePath = outPath + aliquot_uuid + "-" + suffix_id + "." + this.getFormat();
                        try {
                            Files.write((new File(filePath)).toPath(), (FormatUtils.initDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.CREATE);
                            /** store entries **/
                            HashMap<Integer, HashMap<Integer, ArrayList<ArrayList<String>>>> dataMapChr = new HashMap<>();
                            
                            InputStream fstream = new FileInputStream(f.getAbsolutePath());
                            DataInputStream in = new DataInputStream(fstream);
                            BufferedReader br = new BufferedReader(new InputStreamReader(in));
                            String line;
                            boolean firstLine = true;
                            while ((line = br.readLine()) != null) {
                                if (firstLine)
                                    firstLine = false; // just skip the first line (header)
                                else {
                                    String[] line_split = line.split("\t");
                                    String mirna_id = line_split[0];
                                    String read_count = line_split[1];
                                    String reads_per_million_mirna_mapped = line_split[2];
                                    String cross_mapped = line_split[3];

                                    HashMap<String, String> coordinates = null;
                                    if (mirnaid2coordinates.containsKey(mirna_id)) {
                                        coordinates = mirnaid2coordinates.get(mirna_id);

                                        String chr = coordinates.get("CHR");
                                        if (!chr.toLowerCase().contains("chr")) chr = "chr"+chr;
                                        String start = String.valueOf((int)Double.parseDouble(coordinates.get("START")));
                                        String end = String.valueOf((int)Double.parseDouble(coordinates.get("END")));
                                        String strand = coordinates.get("STRAND");
                                        String entrez = "NA";
                                        String symbol = "NA";
                                        
                                        // retrieve entrez_id from GeneNames (HUGO)
                                        String entrez_tmp = GeneNames.getEntrezFromMirnaID(mirna_id);
                                        if (entrez_tmp != null) {
                                            entrez = entrez_tmp;
                                            String symbol_tmp = GeneNames.getSymbolFromEntrez(entrez);
                                            if (symbol_tmp != null)
                                                symbol = symbol_tmp;
                                        }

                                        ArrayList<String> values = new ArrayList<>();
                                        values.add(parseValue(chr, 0));
                                        values.add(parseValue(start, 1));
                                        values.add(parseValue(end, 2));
                                        values.add(parseValue(strand, 3));
                                        values.add(parseValue(mirna_id, 4));
                                        values.add(parseValue(read_count, 5));
                                        values.add(parseValue(reads_per_million_mirna_mapped, 6));
                                        values.add(parseValue(cross_mapped, 7));
                                        values.add(parseValue(entrez, 8));
                                        values.add(parseValue(symbol, 9));
                                        
                                        /**********************************************************************/
                                        /** populate dataMap then sort genomic coordinates and print entries **/
                                        int chr_id = Integer.parseInt(parseValue(chr, 0).replaceAll("chr", "").replaceAll("X", "23").replaceAll("Y", "24"));
                                        int start_id = Integer.parseInt(parseValue(start, 1));
                                        HashMap<Integer, ArrayList<ArrayList<String>>> dataMapStart = new HashMap<>();
                                        ArrayList<ArrayList<String>> dataList = new ArrayList<>();
                                        if (dataMapChr.containsKey(chr_id)) {
                                            dataMapStart = dataMapChr.get(chr_id);                                        
                                            if (dataMapStart.containsKey(start_id))
                                                dataList = dataMapStart.get(start_id);
                                            dataList.add(values);
                                        }
                                        else
                                            dataList.add(values);
                                        dataMapStart.put(start_id, dataList);
                                        dataMapChr.put(chr_id, dataMapStart);
                                        /**********************************************************************/

                                        // decomment this line to print entries without sorting genomic coordinates
                                        //Files.write((new File(outPath + aliquot_uuid + "." + this.getFormat())).toPath(), (FormatUtils.createEntry(this.getFormat(), values, getHeader())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                    }
                                }
                            }
                            br.close();
                            in.close();
                            fstream.close();

                            // sort genomic coordinates and print data
                            this.printData((new File(filePath)).toPath(), dataMapChr, this.getFormat(), getHeader());
                            
                            Files.write((new File(filePath)).toPath(), (FormatUtils.endDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                            filesPathConverted.add(filePath);
                        }
                        catch (Exception e) {
                            error_inputFile2outputFile.put(f, new File(filePath));
                            e.printStackTrace();
                        }
                    }
                    else {
                        System.err.println("ERROR: an error has occurred while retrieving the aliquot UUID for :" + file_uuid);
                        GUI.appendLog(this.getLogger(), "ERROR: an error has occurred while retrieving the aliquot UUID for :" + file_uuid);
                    }
                }
            }
        }
        
        printErrorFileLog(error_inputFile2outputFile);
        
        if (!filesPathConverted.isEmpty()) {
            // write header.schema
            try {
                System.err.println("\n" + "Generating header.schema");
                GUI.appendLog(this.getLogger(), "\n" + "Generating header.schema" + "\n");
                Files.write((new File(outPath + "header.schema")).toPath(), (FormatUtils.generateDataSchema(this.getHeader(), this.getAttributesType())).getBytes("UTF-8"), StandardOpenOption.CREATE);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return 0;
    }

    @Override
    public String[] getHeader() {
        String[] header = new String[10];
        header[0] = "chrom";
        header[1] = "start";
        header[2] = "end";
        header[3] = "strand";
        header[4] = "mirna_id";
        header[5] = "read_count";
        header[6] = "reads_per_million_mirna_mapped";
        header[7] = "cross_mapped";
        header[8] = "entrez_gene_id";
        header[9] = "gene_symbol";
        return header;
    }

    @Override
    public String[] getAttributesType() {
        String[] attr_type = new String[10];
        attr_type[0] = "STRING";
        attr_type[1] = "LONG";
        attr_type[2] = "LONG";
        attr_type[3] = "CHAR";
        attr_type[4] = "STRING";
        attr_type[5] = "LONG";
        attr_type[6] = "DOUBLE";
        attr_type[7] = "STRING";
        attr_type[8] = "STRING";
        attr_type[9] = "STRING";
        return attr_type;
    }

    @Override
    public void initAcceptedInputFileFormats() {
        this.acceptedInputFileFormats = new HashSet<>();
        this.acceptedInputFileFormats.add(".txt");
    }
    
}
