package com.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import java.io.*;
import org.apache.lucene.search.similarities.BM25Similarity;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.GZIPInputStream;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
//NUEVA PARTE
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.taxonomy.*;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;

// IDIOMA
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
    
    public class Indexar {
        public String tipoDato;
        public String directorio;
        public Analyzer analizador;
        public IndexWriter writer;
        public static final String INDEX_PATH = "./index";
        Map<String, StringBuilder> reviewMap = new HashMap<>();
        Map<String, StringBuilder> summaryMap = new HashMap<>();
        public int order=0;
        public DirectoryTaxonomyWriter taxonomyWriter;
        public FacetsConfig configFacetas;
        
        public Indexar(String tipoDato, String directorio, Analyzer defaultAnalyzer, String modo) throws IOException {
            this.tipoDato = tipoDato;
            this.directorio = directorio;
            

            Map<String, Analyzer> analizadores = new HashMap<>();
            analizadores.put("asin", new KeywordAnalyzer());
            analizadores.put("reviewerName", new KeywordAnalyzer());
            

            //this.analyzer = new PerFieldAnalyzerWrapper(defaultAnalyzer, analizadores);
            this.analizador = new StandardAnalyzer();

            Directory dir = FSDirectory.open(Paths.get(INDEX_PATH + "/" + tipoDato));
            Directory dir2 = FSDirectory.open(Paths.get(INDEX_PATH + "/" + "facetas"));
            taxonomyWriter = new DirectoryTaxonomyWriter(dir2);
            IndexWriterConfig iwc = new IndexWriterConfig(this.analizador);
            iwc.setSimilarity(new BM25Similarity());

            if (modo.equalsIgnoreCase("create")) {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            } else if (modo.equalsIgnoreCase("append")) {
                iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            } else {
                throw new IllegalArgumentException("Modo no válido: use 'create' o 'append'");
            }
            writer = new IndexWriter(dir, iwc);
           
            this.configFacetas = new FacetsConfig();
            this.configFacetas.setMultiValued("categorias", true);
            this.configFacetas.setHierarchical("categorias", true);
            this.configFacetas.setMultiValued("rangoPrecio", false);
            this.configFacetas.setMultiValued("rangoAverageRating", false);
            
        }
        public static void main(String[] args) {
        
            if (args.length < 3) {
                System.out.println("Uso: java -cp 'bin:.:lib/*' IndexApp.jar <tipoDato> <directorio> <modo>");
                System.out.println("<tipoDato>: 'reviews/' o 'reviewsEspecial/' o 'metadata/'");
                System.out.println("<directorio>: ruta del directorio con archivos JSON");
                System.out.println("<modo>: 'create' o 'append'");
                System.exit(1);
            }

            String tipoDato = args[0];
            String directorio = args[1];
            String modo = args[2];
            

            try {
                Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
                fieldAnalyzers.put("asin", new KeywordAnalyzer());
                fieldAnalyzers.put("categories", new KeywordAnalyzer());
                fieldAnalyzers.put("reviewerName", new KeywordAnalyzer());
                fieldAnalyzers.put("price", new KeywordAnalyzer());
                fieldAnalyzers.put("ratingCount", new KeywordAnalyzer());
                fieldAnalyzers.put("averageRating", new KeywordAnalyzer());

                //Analyzer analyzer = new PerFieldAnalyzerWrapper(new ASCIIFoldingFilter(), fieldAnalyzers);
                Analyzer analyzer = new StandardAnalyzer();
                Indexar indexManager = new Indexar(tipoDato, directorio, analyzer, modo);
                
                indexManager.configurarIndice();
                indexManager.indexarDocumentos();
                indexManager.close();
                
                System.out.println("Indexación completada");
                
                

                
            } 
             catch (IOException e) {
                System.err.println("Error de entrada/salida: " + e.getMessage());
            }
        }
        public void configurarIndice() {
            System.out.println("Configuracion del indice para tipo de dato: " + tipoDato  );
        }

        public void indexarDocumentos() throws IOException {
            File carpeta = new File(directorio);
            File[] subdirectorios = carpeta.listFiles(File::isDirectory);
            

            if (subdirectorios == null) {
                System.err.println("No se encontraron subdirectorios en el directorio especificado.");
                return;
            }

            ObjectMapper mapper = new ObjectMapper();

            for (File subdirectorio : subdirectorios) {
                System.out.println("Procesando subdirectorio: " + subdirectorio.getName());

                File[] archivos = subdirectorio.listFiles((dir, name) -> name.toLowerCase().endsWith(".gz"));
                if (archivos == null) {
                    System.err.println("No se encontraron archivos .gz en el subdirectorio " + subdirectorio.getName());
                    continue;
                }

                for (File archivo : archivos) {
                    System.out.println("Procesando archivo: " + archivo.getAbsolutePath());
                    try (GZIPInputStream gis = new GZIPInputStream(new FileInputStream(archivo));
                        BufferedReader br = new BufferedReader(new InputStreamReader(gis))) {
                        String linea;
                        while ((linea = br.readLine()) != null) {
                            if (linea.trim().isEmpty()) {
                                System.err.println("Linea vacia en el archivo " + archivo.getName() + ", se omite.");
                                continue;
                            }
                            try {
                                JsonNode nodo = mapper.readTree(linea);

                                if (nodo.isArray()) {
                                    int cont=0;
                                    String aux="";
                                    for (JsonNode jsonObject : nodo) {
                                        String asin = jsonObject.path("asin").asText("");
                                        if(!aux.equals(asin)){
                                            cont=0;
                                            aux = asin;
                                        }
                                        if(cont==0){
                                            procesarDocumento(jsonObject);
                                            cont++;
                                            finalizarIndexado();
                                        }
                                        
                                    }
                                } else if (nodo.isObject()) {
                                    procesarDocumento(nodo);
                                    
                                } else {
                                    System.err.println("Formato inesperado en línea: " + linea);
                                }
                                
                            } catch (Exception jsonException) {
                                System.err.println("Error de formato JSON en línea: " + linea + " - " + jsonException.getMessage());
                            }
                        }
                    } catch (IOException e) {
                        System.err.println("Error leyendo el archivo " + archivo.getName() + ": " + e.getMessage());
                    }
                }
                
            }
        }

        public static String analizarTexto(Analyzer analyzer, String text) throws IOException {
            StringBuilder tokensAsString = new StringBuilder();
            LanguageDetector detector = new org.apache.tika.langdetect.optimaize.OptimaizeLangDetector().loadModels();
            LanguageResult resultado = detector.detect(text);
            String idioma = resultado.getLanguage();
            
            //seleccionamoss el analyzer adecuado según el idioma detectado
            switch (idioma) {
                case "es":
                    analyzer = new SpanishAnalyzer();
                    break;
                case "en":
                    analyzer = new EnglishAnalyzer();
                    
                    break;
                case "fr":
                    analyzer = new FrenchAnalyzer();
                    break;
                default:
                    analyzer = new SpanishAnalyzer();
                    
                break;
            }

            try (var tokenStream = analyzer.tokenStream("field", text)) {
                CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
                tokenStream.reset();

                //concatenar cada token generado por el analizador
                while (tokenStream.incrementToken()) {
                    if (tokensAsString.length() > 0) {
                        tokensAsString.append(" "); 
                    }
                    tokensAsString.append(charTermAttribute.toString());
                }

                tokenStream.end();
            }

            return tokensAsString.toString();
        }
        
        public void procesarDocumento(JsonNode jsonObject) {
            Document doc = new Document();
            
            try {
                if (tipoDato.equalsIgnoreCase("reviews")) {
                    if (jsonObject.hasNonNull("reviewerID") && jsonObject.hasNonNull("asin")) {
                        doc.add(new StringField("asin", jsonObject.path("asin").asText(""), Field.Store.YES));
                        doc.add(new StringField("reviewerID", jsonObject.path("reviewerID").asText(""), Field.Store.YES));
                        String reviewsText = jsonObject.path("reviewText").asText("");
                        analizarTexto(analizador, reviewsText);
                        doc.add(new TextField("reviewerName", jsonObject.path("reviewerName").asText(""), Field.Store.YES));
                        doc.add(new TextField("reviewText", reviewsText, Field.Store.YES)); 
                        doc.add(new StringField("overall",jsonObject.path("overall").asText(""),Field.Store.YES));
                        
                        String summary2 = jsonObject.path("reviewText").asText("");
                        analizarTexto(analizador, summary2);
                        doc.add(new TextField("summary", summary2, Field.Store.YES));  
                        doc.add(new StringField("order", String.valueOf(order), Field.Store.YES));

                        String reviewText = jsonObject.path("reviewText").asText("");
                        String summary = jsonObject.path("summary").asText("");
                        String todosReview = reviewText+ " " + summary;
                        doc.add(new TextField("todosReview", todosReview, Field.Store.YES));
                        writer.addDocument(doc);
                        order++;
                        
                    } else {
                        System.err.println("Documento omitido: faltan campos obligatorios 'reviewerID' o 'asin'.");
                    }

                }
                else if (tipoDato.equalsIgnoreCase("reviewsEspecial")) {
                    

                    String asin = jsonObject.path("asin").asText("");
                    String summary = jsonObject.path("summary").asText("");
                    String reviewText = jsonObject.path("reviewText").asText("");
                    StringBuilder review = new StringBuilder();
                    StringBuilder summary2 = new StringBuilder();
                    if(reviewMap.containsKey(asin)){
                        review.append(reviewMap.get(asin));
                        summary2.append(summaryMap.get(asin));
                        review.append(" ");
                        review.append(reviewText);
                        summary2.append(" ");
                        summary2.append(summary);
                    }
                    else{
                        review.append(reviewText);
                        review.append(" ");
                        summary2.append(summary);
                        summary2.append(" ");
                    }
                    reviewMap.put(asin, review);
                    summaryMap.put(asin, summary2);

                   
                } 
                else if (tipoDato.equalsIgnoreCase("metadata")) {
                    if (jsonObject.hasNonNull("asin")) {
                        doc.add(new StringField("asin", jsonObject.path("asin").asText(""), Field.Store.YES));
                        String title = jsonObject.path("title").asText("");
                    
                        doc.add(new TextField("title", title, Field.Store.YES));
                        
                        String avgRatingStr = jsonObject.path("averageRating").asText("");
                        float averageRating = avgRatingStr.isEmpty() ? 0 : Float.parseFloat(avgRatingStr.replace(",", "."));

                        doc.add(new FloatPoint("averageRating", averageRating));
                        doc.add(new NumericDocValuesField("averageRating", Float.floatToIntBits(averageRating)));
                        doc.add(new StringField("averageRating", avgRatingStr, Field.Store.YES));
                        char coma = ',';
                        char punto = '.';
                        
                        String rangoAvg;
                        float aR = averageRating;
                        if (aR >= 0.0 && aR <= 1.0) {
                            rangoAvg = "0-1";
                        } else if (aR > 1.0 && aR <= 2.0) {
                            rangoAvg = "1-2";
                        } else if (aR > 2.0 && aR <= 3.0) {
                            rangoAvg = "2-3";
                        } else if (aR > 3.0 && aR <= 4.0) {
                            rangoAvg = "3-4";
                        } else if (aR > 4.0 && aR <= 5.0) {
                            rangoAvg = "4-5";
                        } else{
                            rangoAvg = "";
                        }
                        
                        doc.add(new TextField("rangoAverageRating", rangoAvg, Field.Store.YES));
                        doc.add(new FacetField("rangoAverageRating", rangoAvg));
            
                        String ratingCountStr = jsonObject.path("ratingCount").asText("").replaceAll("[^\\d]", "");
                        int ratingCount = ratingCountStr.isEmpty() ? 0 : Integer.parseInt(ratingCountStr);
                        doc.add(new IntPoint("ratingCount", ratingCount));
                        doc.add(new NumericDocValuesField("ratingCount", ratingCount));
                        doc.add(new StringField("ratingCount",String.valueOf(ratingCount),Field.Store.YES));
                        
                        String priceStr = jsonObject.path("price").asText(""); 
                        String priceStr2 = "";
                        if (priceStr.isEmpty()) {
                            priceStr2 = "Precio no estipulado";
                        }
                        else{
                            for(char c : priceStr.toCharArray()){
                                if(c == '.'){
                                    
                                }
                                else if(c == '\u00a0'){
                                    break;
                                }
                                else{
                                    priceStr2 += c;
                                }
                            }
                        }
                        doc.add(new StringField("price", priceStr2, Field.Store.YES));
                        
                        String rangoPrecio="";
                        if(priceStr.isEmpty()){
                            rangoPrecio = "Precio no estipulado";
                        }
                        else{
                            float precio = Float.parseFloat(priceStr2.replace(coma, punto));
                            if(precio>1000){
                                rangoPrecio = "+1000";
                            }
                            else if (precio > 0.0 && precio <= 10.0) {
                                rangoPrecio = "0-10";
                            } else if (precio > 10.0 && precio <= 50.0) {
                                rangoPrecio = "10-50";
                            } else if (precio > 50.0 && precio <= 150.0) {
                                rangoPrecio = "50-150";
                            } else if (precio > 150.0 && precio <= 300.0) {
                                rangoPrecio = "150-300";
                            } else if (precio > 300.0 && precio <= 700.0) {
                                rangoPrecio = "300-700";
                            } else if (precio > 700.0 && precio <= 1000.0) {
                                rangoPrecio = "700-1000";
                            } 
                        }
                        
                        doc.add(new TextField("rangoPrecio",rangoPrecio,Field.Store.YES));
                        doc.add(new FacetField("rangoPrecio", rangoPrecio));
                        String categorias = (jsonObject.path("categories").toString());
                        String categorias2 = analizarTexto(analizador, categorias);
                        doc.add(new TextField("categories", categorias2, Field.Store.YES));

                        String[] categoriasArray = categorias
                            .replace("[", "") 
                            .replace("]", "")
                            .replace("\"", "") 
                            .split(",");

                        for (String categoria : categoriasArray) {
                            
                            if (categoria == null || categoria.trim().isEmpty()) {
                                
                                categoria = "Otros";
                            }
                            
                            String[] categoriaNiveles = categoria.split(" / ");
                            doc.add(new FacetField("categorias", categoriaNiveles));
                        }

                        String descripcion = jsonObject.path("description").asText("");
                        doc.add(new TextField("descripcion", descripcion, Field.Store.YES));
                        doc.add(new StringField("order", String.valueOf(order), Field.Store.YES));
                        writer.addDocument(configFacetas.build(taxonomyWriter, doc));
                        order++;
                    } 
                }
                else if (tipoDato.equalsIgnoreCase("metadataEspecial")) {
                    if (jsonObject.hasNonNull("asin")) {

                        doc.add(new StringField("asin", jsonObject.path("asin").asText(""), Field.Store.YES));
                        String title = jsonObject.path("title").asText("");
                        doc.add(new TextField("tituloAnalizado", analizarTexto(analizador, title), Field.Store.YES));

                        String descripcion = jsonObject.path("description").asText("");
                        descripcion = analizarTexto(analizador, descripcion);
                        doc.add(new TextField("descripcionAnalizado", descripcion, Field.Store.YES));

                        String categorias = (jsonObject.path("categories").toString());
                        categorias = analizarTexto(analizador, categorias);
                        doc.add(new TextField("categoriasAnalizado", categorias, Field.Store.YES));
                        writer.addDocument(configFacetas.build(taxonomyWriter, doc));
                    }
                }
            } catch (Exception e) {
                System.err.println("Error al indexar documento: " + e.getMessage());
            }
        }
        
        public int doc_metadata_actual = 0;
        
        public void finalizarIndexado() throws IOException {
           
            //iterar sobre los documentos y agregar al índice
            for (Map.Entry<String, StringBuilder> entry : reviewMap.entrySet()) {
                String asin = entry.getKey();
                StringBuilder review = entry.getValue();
                StringBuilder summary = summaryMap.get(asin);
                Document doc = new Document();
                doc.add(new TextField("order", String.valueOf(doc_metadata_actual), Field.Store.YES));
                doc.add(new TextField("reviewText", review.toString().trim(), Field.Store.YES));
                doc.add(new TextField("summary", summary.toString().trim(), Field.Store.YES));
                doc.add(new StringField("asin", asin, Field.Store.YES));
                doc_metadata_actual++;
                writer.addDocument(doc);
            }
            reviewMap.clear();
        }
       

        public void close() {
            try {
                writer.forceMerge(1);
                writer.close();
                taxonomyWriter.close();
            } catch (IOException e) {
                System.err.println("Error cerrando el índice o el taxonomyWriter: " + e.getMessage());
            }
        }
    }
    
       
    
    