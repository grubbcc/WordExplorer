package explorer;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.io.*;
import java.util.*;


/**
 * A utility allowing the user to create WordTrees and read definitions
 *
 */

public class WordExplorer extends Application {

    private String lexicon;
    private final TextField textField = new TextField();
    private final Button goButton = new Button("Go");
    private final String[] lexicons = {"CSW19", "NWL18", "LONG"};
    private final ComboBox<String> lexiconSelector = new ComboBox<>(FXCollections.observableArrayList(lexicons));

    private final HashMap<String, AlphagramTrie> tries = new HashMap<>();
    private WordTree tree;
    private final TextArea messagePane = new TextArea();
    private final HBox controlPanel = new HBox();
    private final StackPane treePanel = new StackPane();
    private final BorderPane messagePanel = new BorderPane();
    private final ContextMenu contextMenu = new ContextMenu();
    private final ScrollPane treeSummaryScrollPane = new ScrollPane();
    private Stage stage;

    private final TreeMap<Integer, Integer> counts = new TreeMap<>();
    private final LinkedList<String> wordList = new LinkedList<>();

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        stage.setTitle("Word Explorer");
        AlphagramTrie trie = new AlphagramTrie("CSW19");
        this.lexicon = trie.lexicon;
        tries.put(lexicon, trie);

        //Top panel
        textField.setOnAction(e -> {
            goButton.arm();
            goButton.fire();
            PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
            pause.setOnFinished(ef -> goButton.disarm());
            pause.play();
        });
        textField.setPrefSize(170, 20);

        goButton.setPrefSize(50, 20);
        goButton.setOnAction(e -> {
            if (textField.getText().length() < 4)
                messagePane.setText("You must enter a word of 4 or more letters.");
            else
                lookUp(textField.getText());
        });

        lexiconSelector.setPrefSize(75, 20);
        lexiconSelector.setValue(lexicon);
        lexiconSelector.setOnAction(e -> {
            lexicon = lexiconSelector.getValue();
            tries.computeIfAbsent(lexicon, key -> new AlphagramTrie(lexicon));
        });
        controlPanel.setId("control-panel");
        controlPanel.getChildren().addAll(textField, goButton, lexiconSelector);

        //Main panel
        treePanel.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
        treePanel.setMinHeight(70);
        MenuItem textOption = new MenuItem("Save List to File");
        textOption.setOnAction(e-> saveListToFile());
        MenuItem imageOption = new MenuItem("View List as Image");
        imageOption.setOnAction(e -> viewListAsImage());
        contextMenu.getItems().addAll(textOption, imageOption);

        //Message panel
        messagePanel.setCenter(messagePane);
        messagePanel.setPrefHeight(100);
        messagePane.setEditable(false);
        messagePane.setWrapText(true);
        messagePanel.setId("message-area");
        messagePanel.setStyle("-fx-background-color: rgb(20,250,20)");
        messagePane.setStyle("-fx-background-color: rgb(20,250,20);" + "-fx-text-fill: black");

        BorderPane mainPanel = new BorderPane();
        mainPanel.setTop(controlPanel);
        mainPanel.setCenter(treePanel);
        mainPanel.setBottom(messagePanel);

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.VERTICAL);
        splitPane.getItems().addAll(mainPanel, messagePanel);
        splitPane.setDividerPosition(0, 0.75);

        treeSummaryScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);


        //Scene
        Scene scene = new Scene(splitPane, 345, 415);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN), stage::close);
        scene.setOnKeyTyped(event -> {
            if(event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });
        scene.getStylesheets().add(getClass().getResource("/explorer.css").toExternalForm());
        stage.setMinHeight(260);
        stage.setMinWidth(345);
        stage.setScene(scene);
        stage.show();
    }

    /**
     *
     */

    public void lookUp(String query) {
        textField.clear();
        treePanel.getChildren().clear();
        counts.clear();
        tree = new WordTree(query.toUpperCase(), tries.get(lexicon));

        TreeItem<TreeNode> root = new TreeItem<>(tree.rootNode);
        root.setExpanded(true);

        TreeView<TreeNode> treeView = new TreeView<>(root);
        treeView.setContextMenu(contextMenu);
        treeView.setCellFactory(tv -> new CustomTreeCell());
        treeView.getSelectionModel().selectedItemProperty().addListener((observable, oldItem, newItem) -> {
            if(newItem != null) {
                String definition = tree.trie.getDefinition(newItem.getValue().getWord());
                messagePane.setText(Objects.requireNonNullElse(definition, "Definition not available"));
            }
        });
        setUpTree(root, 100);

        treePanel.getChildren().add(treeView);

        String definition = tree.trie.getDefinition(query);
        messagePane.setText(Objects.requireNonNullElse(definition, "Definition not available"));

        if(!tree.rootNode.getChildren().isEmpty()) {
            treeSummaryScrollPane.setContent(treeSummary(counts));
            messagePanel.setRight(treeSummaryScrollPane);
        }
        else {
            messagePanel.setRight(null);
        }
    }


    /**
     * Recursively builds the tree, computing steals and probability of each TreeItem
     *
     * @param parentItem displays the TreeNode whose children are being computed
     * @param prob       the probability of the current parentItem
     */


    public void setUpTree(TreeItem<TreeNode> parentItem, double prob) {
        double norm = 0;
        for (TreeNode child : parentItem.getValue().getChildren()) {

            String nextSteal = child.getLongSteal();

            for (String s : parentItem.getValue().getLongSteal().split("")) {
                nextSteal = nextSteal.replaceFirst(s, "");
            }
            child.setShortSteal(nextSteal);
            norm += ProbCalc.getProbability(nextSteal);
        }

        for(TreeNode child : parentItem.getValue().getChildren()) {
            TreeItem<TreeNode> childItem = new TreeItem<>(child);
            counts.computeIfPresent(child.getWord().length(), (key, val) -> val + 1);
            counts.putIfAbsent(child.getWord().length(), 1);
            parentItem.getChildren().add(childItem);
            child.setProb(prob*ProbCalc.getProbability(child.getShortSteal())/norm);
            setUpTree(childItem, child.getProb());
        }
    }



    /**
     *
     */

    private class CustomTreeCell extends TreeCell<TreeNode> {

        /**
         * Look up a word when it is double-clicked.
         */

        private CustomTreeCell() {
            setOnMouseClicked(e -> {
                if(!isEmpty() && e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                    goButton.arm();
                    PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
                    pause.setOnFinished(ef -> goButton.disarm());
                    pause.play();
                    lookUp(getText());
                }
            });
        }

        /**
         * Color cell according to probability and display tooltip
         */

        @Override
        public void updateItem(TreeNode item, boolean empty) {
            super.updateItem(item, empty);

            if (!empty) {
                setText(item.getWord());
                setTooltip(new Tooltip(item.getLongSteal() + "   " + ProbCalc.round(item.getProb(),1) + "%"));

                setStyle("-cell-background: hsb(0, " + ProbCalc.round(item.getProb(),1) + "%, 100%);");
                if (item.equals(tree.rootNode)) {
                    setText(tree.trie.contains(item.getWord()) ? item.getWord() : item.getWord().toLowerCase());
                    setTooltip(null);
                }
            } else {
                setText(null);
                setTooltip(null);
                setStyle("-cell-background: white;");
            }
        }

    }

    /**
     * Creates a table showing the number of steals of the rootWord organized by word length
     */

    public VBox treeSummary(TreeMap<Integer, Integer> counts) {

        VBox summaryPane = new VBox();

        if(!counts.isEmpty()) {
            GridPane treeSummary = new GridPane();
            treeSummary.getColumnConstraints().add(new ColumnConstraints(45));
            treeSummary.getColumnConstraints().add(new ColumnConstraints(55));
            treeSummary.addRow(0, new Label("  length"), new Label("  words"));

            for (Integer key : counts.keySet()) {
                treeSummary.addRow(key, new Label("  " + key), new Label("  " + counts.get(key)));
            }
            treeSummary.setGridLinesVisible(true);
            treeSummary.setBackground(new Background(new BackgroundFill(Color.WHITE, CornerRadii.EMPTY, Insets.EMPTY)));
            summaryPane.getChildren().add(treeSummary);

        }
        return summaryPane;
    }



    /**
     * Recursively performs a depth-first search of the word tree, saving the
     * words visited to a String.
     *
     * @param prefix Indentations indicating the depth of the node
     * @param node The node currently being visited
     */

    public void generateWordList(String prefix, TreeNode node) {
        for(TreeNode child : node.getChildren()) {
            wordList.add(prefix + child.getWord());
            generateWordList(prefix + "  ", child);
        }
    }



    /**
     *
     */

    private void saveListToFile() {

        generateWordList("", tree.rootNode);

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setInitialFileName(tree.rootWord + ".txt");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text doc(*.txt)", "*.txt"));
        File file = fileChooser.showSaveDialog(stage);
        if(file == null) return;

        try {
            PrintStream out = new PrintStream(new FileOutputStream(file));
            for(String word : wordList)
                out.println(word);
            out.flush();
            out.close();
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    /**
     *
     */

    private void viewListAsImage() {

        if(tree.data.isEmpty())
            tree.generateJSON(tree.rootWord, tree.rootNode);

        WebView browser = new WebView();
        WebEngine webEngine = browser.getEngine();
        webEngine.setJavaScriptEnabled(true);
        webEngine.load(getClass().getResource("/flare.html").toExternalForm());
        webEngine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {

            if (newState == Worker.State.SUCCEEDED) {
                webEngine.executeScript("init('" + tree.data.toString().replaceAll("'", "\\\\'") + "');");
            }
        });

        Stage dialog = new Stage();
        dialog.setTitle(tree.rootWord);
        Scene scene = new Scene(browser, 800, 800);
        dialog.setScene(scene);
        scene.getAccelerators().put(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN), dialog::close);
        scene.setOnKeyTyped(event -> {
            if(event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
            }
        });

        webEngine.setOnAlert(event -> {
            System.out.println(event.getData());
            byte[] decodedBytes = Base64.getMimeDecoder().decode(((event.getData().split(",")[1]).getBytes()));

            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save");
            fileChooser.setInitialFileName(tree.rootWord + ".png");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG files(*.png)", "*.png"));
            fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
            File file = fileChooser.showSaveDialog(dialog);
            if(file == null) return;
            try (OutputStream stream = new FileOutputStream(file)) {
                stream.write(decodedBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        dialog.show();
    }

    /**
     *
     * @param args not used
     */

    public static void main(String[] args) {
        launch(args);
    }
}