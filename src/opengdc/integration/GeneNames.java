package opengdc.integration;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import opengdc.Settings;

/**
 *
 * @author fabio
 */
public class GeneNames {
    
    private static String genenames_table_path = Settings.getGENENAMESDataPath();
    private static HashMap<String, String> symbol2entrez = new HashMap<>();
    private static HashMap<String, String> mirnaid2entrez = new HashMap<>();
    private static HashMap<String, String> ensembl2symbol = new HashMap<>();
    
    public static HashMap<String, String> getSymbol2Entrez() {
        if (symbol2entrez.isEmpty()) {
            try {
                boolean firstLine = true; // just to skip the first line (header)
                InputStream fstream = new FileInputStream(genenames_table_path);
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        if (!firstLine) {
                            String[] arr = line.split("\t");
                            String symbol = arr[1];
                            String entrez = arr[18];
                            String symbol_lower = symbol.trim().toLowerCase();
                            symbol2entrez.put(symbol_lower, entrez);
                        }
                        else
                            firstLine = false;
                    } catch (Exception e) {}
                }
                br.close();
                in.close();
                fstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return symbol2entrez;
    }

     public static HashMap<String, String> getEnsemblId2Symbol() {
        if (ensembl2symbol.isEmpty()) {
            try {
                boolean firstLine = true; // just to skip the first line (header)
                InputStream fstream = new FileInputStream(genenames_table_path);
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        if (!firstLine) {
                            String[] arr = line.split("\t");
                            String ensembl_id = arr[19];
                            String symbol = arr[1];
                            ensembl2symbol.put(ensembl_id, symbol);         
                        }
                        else
                            firstLine = false;
                    } catch (Exception e) {}
                }
                br.close();
                in.close();
                fstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ensembl2symbol;
    }
    
    public static String getSymbolFromEntrez(String entrez) {
        HashMap<String, String> data = getSymbol2Entrez();
        if (!data.isEmpty()) {
            for (String gs: data.keySet()) {
                if (data.get(gs).trim().toLowerCase().equals(entrez.trim().toLowerCase()))
                    return gs;
            }
        }
        return null;
    }
    
    public static HashMap<String, String> getMirnaID2Entrez() {
        if (mirnaid2entrez.isEmpty()) {
            try {
                boolean firstLine = true; // just to skip the first line (header)
                InputStream fstream = new FileInputStream(genenames_table_path);
                DataInputStream in = new DataInputStream(fstream);
                BufferedReader br = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = br.readLine()) != null) {
                    try {
                        if (!firstLine) {
                            String[] arr = line.split("\t");
                            String mirnaid = arr[8];
                            String entrez = arr[18];
                            mirnaid2entrez.put(mirnaid, entrez);
                        }
                        else
                            firstLine = false;
                    } catch (Exception e) {}
                }
                br.close();
                in.close();
                fstream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return mirnaid2entrez;
    }
    
    public static String getEntrezFromSymbol(String symbol) {
        HashMap<String, String> data = getSymbol2Entrez();
        if (!data.isEmpty()) {
            String symbol_lower = symbol.trim().toLowerCase();
            if(data.containsKey(symbol_lower)) return data.get(symbol_lower);
        }
        return null;
    }
    
    public static String getEntrezFromMirnaID(String mirnaid) {
        HashMap<String, String> data = getMirnaID2Entrez();
        if (!data.isEmpty()) {
            for (String mid: data.keySet()) {
                if (mid.trim().toLowerCase().equals(mirnaid.trim().toLowerCase()))
                    return data.get(mid);
            }
        }
        return null;
    }

    public static String getSymbolFromEnsemblID(String ensembl_id) {
        HashMap<String, String> data = getEnsemblId2Symbol();
        if (!data.isEmpty()) {
            if (data.containsKey(ensembl_id)) 
                return data.get(ensembl_id);
        }
        return null;
    }

    public static String getEntrezFromEnsemblID(String ensembl_id) {
        String gene_symbol_tmp = getSymbolFromEnsemblID(ensembl_id);
        if (gene_symbol_tmp != null)
            return getEntrezFromSymbol(gene_symbol_tmp);
        return null; 
    }
    
}
