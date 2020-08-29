/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package opengdc;

import java.util.Calendar;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author fabio
 */
public class UpdateScheduler {
    
    private static final String FTP_ROOT = "/FTP/ftp-root/opengdc/";
    private static final String FTP_REPO = "ftp://150.146.100.179/opengdc/";
    
    public static void main(String[] args) {
        Timer timer = new java.util.Timer();
        Calendar date = Calendar.getInstance();
        /*date.set(Calendar.DAY_OF_WEEK, Calendar.TUESDAY);
        date.set(Calendar.HOUR, 1);
        date.set(Calendar.MINUTE, 0);
        date.set(Calendar.SECOND, 0);*/
        
        /*String inputProgram = null;
        String inputDiseaseAbbreviation = null;
        if (args.length == 2) {
            inputProgram = args[0];
            inputDiseaseAbbreviation = args[1];
        }
        else {
            System.err.println("Usage example: java -jar app.jar 'program' 'disease'");
            System.exit(1);
        }*/
        
        //String inputProgram = "TCGA";
        //String inputDiseaseAbbreviation = "ACC";
        //String inputDisease = (inputProgram+"-"+inputDiseaseAbbreviation).toLowerCase();
        
        String files_datetime = Settings.getFilesDatetime();
        if (args.length == 2) {
            //args[0];
            files_datetime = args[1];
        }
        timer.schedule(new UpdateTask(FTP_ROOT, FTP_REPO, files_datetime), date.getTime(), TimeUnit.DAYS.toMillis(Settings.getUpdateDays()));
    }
    
}
