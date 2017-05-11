/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opengdc.parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import opengdc.GUI;
import opengdc.integration.Ensembl;
import opengdc.reader.GeneExpressionQuantificationReader;
import opengdc.util.FSUtils;
import opengdc.util.FormatUtils;
import opengdc.util.GDCQuery;

/**
 *
 * @author fabio
 */
public class GeneExpressionQuantificationParser extends BioParser {

    @Override
    public int convert(String program, String disease, String dataType, String inPath, String outPath) {
        int acceptedFiles = FSUtils.acceptedFilesInFolder(inPath, getAcceptedInputFileFormats());
        System.err.println("Data Amount: " + acceptedFiles + " files" + "\n\n");
        GUI.appendLog("Data Amount: " + acceptedFiles + " files" + "\n\n");
        
        if (acceptedFiles == 0)
            return 1;
        
        HashSet<String> filesPathConverted = new HashSet<>();
        
        // retrive all aliquot IDs
        HashMap<String, String> fileUUID2aliquotUUID = new HashMap<>();
        File[] files = (new File(inPath)).listFiles();
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                if (getAcceptedInputFileFormats().contains(extension)) {
                    String file_uuid = f.getName().split("_")[0];
                    String aliquot_uuid = GDCQuery.retrieveAliquotFromFileUUID(file_uuid);
                    fileUUID2aliquotUUID.put(file_uuid, aliquot_uuid);
                }
            }
        }
        
        for (File f: files) {
            if (f.isFile()) {
                String extension = FSUtils.getFileExtension(f);
                // start with 'counts' file and manually retrieve the related 'FPKM' and 'FPKM-UQ' files
                if (getAcceptedInputFileFormats().contains(extension) && extension.toLowerCase().trim().equals("counts")) {
                    // TODO: we need a common name for 'counts', 'FPKM' and 'FPKM-UQ' files for the same aliquot
                    String file_uuid = f.getName().split("_")[0];
                    String aliquot_uuid = fileUUID2aliquotUUID.get(file_uuid);
                    if (!aliquot_uuid.trim().equals("")) {
                        // retrieve 'FPKM' and 'FPKM-UQ' files with the same aliquot_uuid (related to the 'counts' file)
                        File fpkm_file = getRelatedFile(files, aliquot_uuid, fileUUID2aliquotUUID, "fpkm.txt");
                        File fpkmuq_file = getRelatedFile(files, aliquot_uuid, fileUUID2aliquotUUID, "fpkm.txt");

                        System.err.println("Processing " + aliquot_uuid + " (counts, FPKM, FPKM-UQ)");
                        GUI.appendLog("Processing " + aliquot_uuid + " (counts, FPKM, FPKM-UQ)" + "\n");

                        HashMap<String, String> ensembl2count = GeneExpressionQuantificationReader.getEnsembl2Value(f);
                        HashMap<String, String> ensembl2fpkm = GeneExpressionQuantificationReader.getEnsembl2Value(fpkm_file);
                        HashMap<String, String> ensembl2fpkmuq = GeneExpressionQuantificationReader.getEnsembl2Value(fpkmuq_file);

                        HashSet<String> ensembls = new HashSet<>();
                        ensembls.addAll(ensembl2count.keySet());
                        ensembls.addAll(ensembl2fpkm.keySet());
                        ensembls.addAll(ensembl2fpkmuq.keySet());

                        if (!ensembls.isEmpty()) {
                            try {
                                Files.write((new File(outPath + aliquot_uuid + "." + this.getFormat())).toPath(), (FormatUtils.initDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.CREATE);
                                for (String ensembl_id: ensembls) {
                                    /** convert ensembl_id to symbol and retrieve chromosome, start and end position, strand, and other relevant info **/
                                    // remove ensembl version from id
                                    String ensembl_id_noversion = ensembl_id.split("\\.")[0];
                                    HashMap<String, String> ensembl_data = Ensembl.extractEnsemblInfo(ensembl_id_noversion);
                                    if (!ensembl_data.isEmpty()) {
                                        String chr = ensembl_data.get("CHR");
                                        String start = ensembl_data.get("START");
                                        String end = ensembl_data.get("END");
                                        String strand = ensembl_data.get("STRAND");
                                        String symbol = ensembl_data.get("SYMBOL");
                                        String type = ensembl_data.get("TYPE");
                                        /***************************************************************************************************/
                                        String htseq_count = (ensembl2count.containsKey(ensembl_id)) ? ensembl2count.get(ensembl_id) : "NA";
                                        String fpkm_uq = (ensembl2fpkmuq.containsKey(ensembl_id)) ? ensembl2fpkmuq.get(ensembl_id) : "NA";
                                        String fpkm = (ensembl2fpkm.containsKey(ensembl_id)) ? ensembl2fpkm.get(ensembl_id) : "NA";

                                        ArrayList<String> values = new ArrayList<>();
                                        values.add(chr);
                                        values.add(start);
                                        values.add(end);
                                        values.add(strand);
                                        values.add(ensembl_id);
                                        values.add(symbol);
                                        values.add(type);
                                        values.add(htseq_count);
                                        values.add(fpkm_uq);
                                        values.add(fpkm);
                                        Files.write((new File(outPath + aliquot_uuid + "." + this.getFormat())).toPath(), (FormatUtils.createEntry(this.getFormat(), values, getHeader())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                    }
                                }
                                Files.write((new File(outPath +  aliquot_uuid + "." + this.getFormat())).toPath(), (FormatUtils.endDocument(this.getFormat())).getBytes("UTF-8"), StandardOpenOption.APPEND);
                                filesPathConverted.add(outPath + file_uuid + "." + this.getFormat());
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    else {
                        System.err.println("ERROR: an error has occurred while retrieving the aliquot UUID for :" + file_uuid);
                        GUI.appendLog("ERROR: an error has occurred while retrieving the aliquot UUID for :" + file_uuid);
                    }
                }
            }
        }
        
        if (!filesPathConverted.isEmpty()) {
            // write header.schema
            try {
                System.err.println("\n" + "Generating header.schema");
                GUI.appendLog("\n" + "Generating header.schema" + "\n");
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
        header[0] = "chr";
        header[1] = "start";
        header[2] = "stop";
        header[3] = "strand";
        header[4] = "ensembl_id";
        header[5] = "symbol";
        header[6] = "type";
        header[7] = "htseq_count";
        header[8] = "fpkm_uq";
        header[9] = "fpkm";
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
        attr_type[5] = "STRING";
        attr_type[6] = "STRING";
        attr_type[7] = "LONG";
        attr_type[8] = "FLOAT";
        attr_type[9] = "FLOAT";
        return attr_type;
    }

    @Override
    public void initAcceptedInputFileFormats() {
        this.acceptedInputFileFormats = new HashSet<>();
        this.acceptedInputFileFormats.add(".txt");
        this.acceptedInputFileFormats.add(".counts");
    }

    private File getRelatedFile(File[] files, String reference_aliquot_uuid, HashMap<String, String> fileUUID2aliquotUUID, String suffix) {
        for (File f: files) {
            if (f.isFile()) {
                if (f.getName().toLowerCase().trim().endsWith(suffix)) {
                    String file_uuid = f.getName().split("_")[0];
                    String aliquot_uuid = fileUUID2aliquotUUID.get(file_uuid);
                    
                    if (aliquot_uuid.equals(reference_aliquot_uuid))
                        return f;
                }
            }
        }
        return null;
    }
    
}
