package com.orfangenes.control;

import com.orfangenes.model.Gene;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class Sequence {
    private List<Gene> genes;
    private int fileCount;

    private static final String GI = ">gi";
    private static final String BLAST_RESULTS = "blastResults";
    private static final String BLAST_EXT = ".bl";
    private static final double SEQUENCE_SLICE_SIZE = 3.0;

    public Sequence (String filename, String out, int inputTax) {
        File sequenceFile = new File(filename);
        if (!sequenceFile.exists()) {
            System.err.println("Failure to open sequence.");
            return;
        }
        this.genes = getGeneData(filename, inputTax);
        this.fileCount = divideSequence(filename, out);
    }

    public void generateBlastFile(String type, String out, String max_target_seqs, String evalue) {
        System.out.println("Running BLAST. Be patient...This will take 2-15 min...");
        long startTime = System.currentTimeMillis();

        BlastCommand[] blastCommands = new BlastCommand[fileCount];
        for (int i = 1; i < fileCount + 1; i++) {
            BlastCommand command = new BlastCommand(Integer.toString(i), type, out, max_target_seqs, evalue);
            command.start();
            blastCommands[i-1] = command;
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Wait for all blast commands to finish running
        try {
            for (BlastCommand command: blastCommands) {
                command.join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        //Combining all BLAST results to one file
        try {
            PrintWriter writer = new PrintWriter(out + "/" + BLAST_RESULTS + BLAST_EXT);
            for (int i = 1; i < fileCount + 1; i++) {
                BufferedReader reader = new BufferedReader(new FileReader(out + "/" + BLAST_RESULTS + Integer.toString(i) + BLAST_EXT));
                String blastResult = reader.readLine();
                while (blastResult != null) {
                    writer.println(blastResult);
                    blastResult = reader.readLine();
                }
                reader.close();
            }
            writer.flush();
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("BLAST successfully Completed!!");
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        System.out.println("Time taken: " + elapsedTime + "ms");
    }

    public List<Gene> getGenes() {
        return genes;
    }

    private ArrayList<Gene> getGeneData(String sequenceFileName, int inputTax) {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines( Paths.get(sequenceFileName), StandardCharsets.UTF_8))
        {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        String inputSequence = contentBuilder.toString();
        String[] sequences = inputSequence.split("\n\n");
        ArrayList<Gene> genes = new ArrayList<>();

        for (String sequence: sequences) {
            Gene gene = new Gene();
            gene.setTaxID(inputTax);

            String[] sequenceData = sequence.split("\\|");
            if (sequenceData.length > 1) {
                int offset = 2;
                if (sequenceData[0].equals(GI)) {
                    gene.setGeneID(Integer.parseInt(sequenceData[1]));
                    offset = 0;
                }

                String geneInfo = sequenceData[4 - offset];
                String[] lines = geneInfo.split("\n");
                gene.setDescription(lines[0]);

                String sequenceString = "";
                for (int i = 1; i < lines.length; i++) {
                    sequenceString += lines[i];
                }
                gene.setSequence(sequenceString);
                genes.add(gene);
            }
        }
        return genes;
    }

    private int divideSequence(String sequenceFileName, String out) {
        StringBuilder contentBuilder = new StringBuilder();
        try (Stream<String> stream = Files.lines( Paths.get(sequenceFileName), StandardCharsets.UTF_8))
        {
            stream.forEach(s -> contentBuilder.append(s).append("\n"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        String inputSequence = contentBuilder.toString();
        String[] sequences = inputSequence.split("\n\n");

        String currentSequence = "";
        int fileNo = 1;
        for (double i = 0; i < (sequences.length - 1); i++) {
            if (((i % SEQUENCE_SLICE_SIZE) == 0) && (i > 0)) {
                createSequenceFile(out, currentSequence, fileNo);
                currentSequence = "";
                fileNo++;
            }

            if (i%SEQUENCE_SLICE_SIZE == (SEQUENCE_SLICE_SIZE - 1.0)) {
                currentSequence += sequences[(int)i] + "\n";
            } else {
                currentSequence += sequences[(int) i] + "\n\n";
            }
        }
        if (!currentSequence.equals("")) {
            createSequenceFile(out, currentSequence, fileNo);
        }
        return fileNo;
    }

    private void createSequenceFile(String out, String sequence, int fileNo) {
        try {
            PrintWriter writer = new PrintWriter(out + "/sequence" + Integer.toString(fileNo) + ".fasta", "UTF-8");
            writer.println(sequence);
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

    }
}
