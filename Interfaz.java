package com.example;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;


import org.apache.lucene.document.Document;
import org.apache.lucene.facet.*;
import org.apache.lucene.facet.taxonomy.*;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.tika.language.detect.LanguageDetector;
import org.apache.tika.language.detect.LanguageResult;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.es.SpanishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.ParallelCompositeReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;

public class Interfaz {
    private static final int PAGE_SIZE = 30; 
    private IndexSearcher searcherParalelo;
    private FacetsConfig facetsConfig;
    private TaxonomyReader taxoReader;
    private List<JCheckBox> precioCheckBoxes = new ArrayList<>();
    private List<JCheckBox> ratingCheckBoxes = new ArrayList<>();
    private int paginaActual = 0; 
    private List<Document> documentos = new ArrayList<>(); 
    private JTextField buscador; 

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new Interfaz().crearInterfaz();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public Interfaz() throws IOException {
        String metadataPath = "./index/metadata";
        String facetsPath = "./index/facetas";
        String reviewsPath = "./index/reviewsEspecial";
        String metadataEspecialPath = "./index/metadataEspecial";

        FSDirectory metadataDirectory = FSDirectory.open(Paths.get(metadataPath));
        FSDirectory reviewsDirectory = FSDirectory.open(Paths.get(reviewsPath));
        FSDirectory facetsDirectory = FSDirectory.open(Paths.get(facetsPath));
        FSDirectory metadataEspecialDirectory = FSDirectory.open(Paths.get(metadataEspecialPath));

        DirectoryReader metadataReader = DirectoryReader.open(metadataDirectory);
        DirectoryReader reviewsReader = DirectoryReader.open(reviewsDirectory);
        DirectoryReader metadataEspecialReader = DirectoryReader.open(metadataEspecialDirectory);

        ParallelCompositeReader paralelo = new ParallelCompositeReader(metadataReader,reviewsReader,metadataEspecialReader);

        taxoReader = new DirectoryTaxonomyReader(facetsDirectory);
        searcherParalelo = new IndexSearcher(paralelo);

        facetsConfig = new FacetsConfig();
        facetsConfig.setMultiValued("rangoPrecio", false);
        facetsConfig.setMultiValued("rangoAverageRating", false);
        facetsConfig.setMultiValued("categorias", true);
    }

    private void anadirFiltros(DrillDownQuery query, String field, List<JCheckBox> checkBoxList) {
        for (JCheckBox checkBox : checkBoxList) {
            if (checkBox.isSelected()) {
                query.add(field, checkBox.getText().split(" \\(")[0]); // Extraer el texto antes del paréntesis
            }
        }
    }

    private void crearInterfaz() {
        JFrame frame = new JFrame("Interfaz METADATA y REVIEWS");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 600);

        JPanel mainPanel = new JPanel(new BorderLayout());

        JTree categoryTree = arbolCategorias();
        JScrollPane treeScrollPane = new JScrollPane(categoryTree);
        treeScrollPane.setBorder(BorderFactory.createTitledBorder("Categorías"));

        JPanel filterPanel = new JPanel();
        filterPanel.setLayout(new BoxLayout(filterPanel, BoxLayout.Y_AXIS));
        filterPanel.setBorder(BorderFactory.createTitledBorder("Filtros Adicionales"));

        filterPanel.add(new JLabel("Rango de Precios:"));
        anadirFacetas(filterPanel, "rangoPrecio", precioCheckBoxes);

        filterPanel.add(new JLabel("Rango de Valoraciones Promedio:"));
        anadirFacetas(filterPanel, "rangoAverageRating", ratingCheckBoxes);

        //Campo de busqueda
        buscador = new JTextField(50);
        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        searchPanel.add(new JLabel("Buscar:"));
        searchPanel.add(buscador);
        buscador.setMaximumSize(new Dimension(200, 30));

        //Tabla para mostrar documentos
        JTable table = new JTable(new DefaultTableModel(new Object[] { "Título","Descripción", "Precio","Calificación promedio", "Resumen" }, 0));
        JScrollPane tableScrollPane = new JScrollPane(table);

        //Boton "Aplicar Filtros"
        JButton applyFiltersButton = new JButton("Aplicar Filtros");
        applyFiltersButton.addActionListener(e -> {
            try {
                DrillDownQuery drillDownQuery = contruirConsulta(categoryTree);
                anadirFiltros(drillDownQuery, "rangoPrecio", precioCheckBoxes);
                anadirFiltros(drillDownQuery, "rangoAverageRating", ratingCheckBoxes);

                documentos = buscarIndice(drillDownQuery,buscador.getText());
                paginaActual = 0;
                actualizarTabla(table);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        //Combinar el campo de búsqueda y el botón en un panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(searchPanel, BorderLayout.WEST);
        topPanel.add(applyFiltersButton, BorderLayout.EAST);

        //Botones de paginación
        JPanel paginationPanel = new JPanel(new FlowLayout());
        JButton prevButton = new JButton("← Anterior");
        JButton nextButton = new JButton("Siguiente →");

        prevButton.addActionListener(e -> {
            if (paginaActual > 0) {
                paginaActual--;
                actualizarTabla(table);
            }
        });

        nextButton.addActionListener(e -> {
            if ((paginaActual + 1) * PAGE_SIZE < documentos.size()) {
                paginaActual++;
                actualizarTabla(table);
            }
        });

        paginationPanel.add(prevButton);
        paginationPanel.add(nextButton);

        mainPanel.add(treeScrollPane, BorderLayout.WEST);
        mainPanel.add(filterPanel, BorderLayout.EAST);
        mainPanel.add(tableScrollPane, BorderLayout.CENTER);
        mainPanel.add(paginationPanel, BorderLayout.SOUTH);
        mainPanel.add(topPanel, BorderLayout.NORTH); 

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    private void actualizarTabla(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);

        int start = paginaActual * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, documentos.size());

        for (int i = start; i < end; i++) {
            Document doc = documentos.get(i);
            model.addRow(new Object[] { doc.get("title"), doc.get("descripcion"), doc.get("price"), doc.get("averageRating"), doc.get("summary")});
            
        }
    }

    private void anadirFacetas(JPanel panel, String facetField, List<JCheckBox> checkBoxList) {
        try {
            FacetsCollector facetsCollector = new FacetsCollector();
            searcherParalelo.search(new MatchAllDocsQuery(), facetsCollector);

            Facets facets = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, facetsCollector);
            FacetResult facetResult = facets.getTopChildren(10, facetField);

            if (facetResult != null && facetResult.labelValues != null) {
                for (LabelAndValue lv : facetResult.labelValues) {
                    JCheckBox checkBox = new JCheckBox(lv.label + " (" + lv.value + ")");
                    checkBoxList.add(checkBox);
                    panel.add(checkBox);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private DrillDownQuery contruirConsulta(JTree categoryTree) throws ParseException {
        Query baseQuery = new MatchAllDocsQuery(); 

        //Agregar la consulta de búsqueda
        String queryText = buscador.getText();
        queryText = quitarTildes(queryText);
        if (queryText != null && !queryText.trim().isEmpty()) {
            LanguageDetector detector = new org.apache.tika.langdetect.optimaize.OptimaizeLangDetector().loadModels();
            LanguageResult result = detector.detect(queryText);
            String languageCode = result.getLanguage();
            System.out.println("Idioma detectado: " + languageCode);
            Analyzer analyzer = new StandardAnalyzer();
            //Seleccionar el analyzer adecuado según el idioma detectado
            switch (languageCode) {
                case "es":
                    analyzer = new SpanishAnalyzer();
                   
                    break;
                case "en":
                    analyzer = new EnglishAnalyzer();
                    
                    break;
                default:
                    analyzer = new SpanishAnalyzer();
                    
                break;
            }
            
           QueryParser parser = new QueryParser("tituloAnalizado", analyzer);
           Query tilequQuery = parser.parse(queryText);
           tilequQuery = new BoostQuery(tilequQuery, 10.0f);
           
           QueryParser parser2 = new QueryParser("descripcionAnalizado", analyzer);
           Query descQuery = parser2.parse(queryText);
           descQuery = new BoostQuery(descQuery, 5f);
           QueryParser parser3 = new QueryParser("summary", new SpanishAnalyzer());
           Query reviewQuery = parser3.parse(queryText);
           reviewQuery = new BoostQuery(reviewQuery, 2f);
           
           BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
            booleanQuery.add(tilequQuery, BooleanClause.Occur.SHOULD);
            booleanQuery.add(descQuery, BooleanClause.Occur.SHOULD);
            booleanQuery.add(reviewQuery, BooleanClause.Occur.SHOULD);

            baseQuery = new BooleanQuery.Builder()
                .add(baseQuery, BooleanClause.Occur.MUST)
                .add(booleanQuery.build(), BooleanClause.Occur.MUST)
                
                .build();
        }

        DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig, baseQuery);

        // Agregar filtros de categoría
        
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) categoryTree.getLastSelectedPathComponent();
        if (selectedNode != null && !selectedNode.isRoot()) {
            String selectedCategory = selectedNode.getUserObject().toString();
            drillDownQuery.add("categorias", selectedCategory);
        }

        return drillDownQuery;
    }

    private List<Document> buscarIndice(Query query, String queryText) throws IOException {
        List<Document> results = new ArrayList<>();
        
        //realizar la búsqueda en el índice paralelo
        TopDocs topDocs = searcherParalelo.search(query, Integer.MAX_VALUE);
        
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            Document doc = searcherParalelo.doc(scoreDoc.doc);
            //asegurarse de que el documento tenga todos los campos necesarios
            String title = doc.get("title");
            String description = doc.get("descripcion");
            String tituloAnalizado = doc.get("tituloAnalizado");
            String descripcionAnalizado = doc.get("descripcionAnalizado");
            String price = doc.get("price");
            String averageRating = doc.get("averageRating");
            String summary = doc.get("summary");
            
            //crear un nuevo documento con los campos necesarios
            Document newDoc = new Document();
            newDoc.add(new TextField("title",title,Field.Store.YES));
            newDoc.add(new TextField("descripcion",description,Field.Store.YES));
            newDoc.add(new TextField("tituloAnalizado",tituloAnalizado,Field.Store.YES));
            newDoc.add(new TextField("descripcionAnalizado",descripcionAnalizado,Field.Store.YES));
            newDoc.add(new TextField("price",price,Field.Store.YES));
            newDoc.add(new TextField("averageRating",averageRating,Field.Store.YES));
            newDoc.add(new TextField("summary",summary,Field.Store.YES));
            
            results.add(newDoc);
        }
        return results;
    }
    public String quitarTildes(String texto) {
        return texto.replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u");
    }
private JTree arbolCategorias() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Categorías");

        try {
            FacetsCollector facetsCollector = new FacetsCollector();
            searcherParalelo.search(new MatchAllDocsQuery(), facetsCollector);

            Facets facets = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, facetsCollector);
            FacetResult categoryFacetResult = facets.getTopChildren(10, "categorias");

            if (categoryFacetResult != null && categoryFacetResult.labelValues != null) {
                for (LabelAndValue lv : categoryFacetResult.labelValues) {
                    DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(lv.label);

                    //obtener subcategorías
                    DrillDownQuery drillDownQuery = new DrillDownQuery(facetsConfig);
                    drillDownQuery.add("categorias", lv.label);
                    FacetsCollector subFacetsCollector = new FacetsCollector();
                    searcherParalelo.search(drillDownQuery, subFacetsCollector);

                    Facets subFacets = new FastTaxonomyFacetCounts(taxoReader, facetsConfig, subFacetsCollector);
                    FacetResult subCategoryFacetResult = subFacets.getTopChildren(10, "categorias");

                    if (subCategoryFacetResult != null && subCategoryFacetResult.labelValues != null) {
                        for (LabelAndValue subLv : subCategoryFacetResult.labelValues) {
                            categoryNode.add(new DefaultMutableTreeNode(subLv.label));
                        }
                    }

                    root.add(categoryNode);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return new JTree(root);
    }
}
