package dev.hephaestus.proximity.app.impl;

import dev.hephaestus.proximity.app.api.RenderJob;
import dev.hephaestus.proximity.app.api.Template;
import dev.hephaestus.proximity.app.api.logging.Log;
import dev.hephaestus.proximity.app.api.plugins.DataProvider;
import dev.hephaestus.proximity.app.api.plugins.DataWidget;
import dev.hephaestus.proximity.app.impl.exceptions.InitializationException;
import dev.hephaestus.proximity.app.impl.exceptions.PluginInstantiationException;
import dev.hephaestus.proximity.app.impl.plugins.Plugin;
import dev.hephaestus.proximity.app.impl.sidebar.OptionsPane;
import dev.hephaestus.proximity.app.impl.sidebar.SidebarPane;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Proximity {
    // TODO: Allow multiple data providers
    private static final Collection<DataProvider<?>> DATA_PROVIDERS = new TreeSet<>(Comparator.comparing(o -> o.getClass().getModule().getName()));

    private static final SelectionManager SELECTION_MANAGER = new SelectionManager();
    private static final SimpleBooleanProperty PAUSED = new SimpleBooleanProperty(false);
    private static final SimpleObjectProperty<SidebarPane> activeCategory = new SimpleObjectProperty<>();
    private static ModuleLayer MODULES;
    private static Plugin DATA_PROVIDER_PLUGIN;
    private static Path WORKING_DIRECTORY;
    private static Path PLUGIN_DIRECTORY;
    private static Log LOG;
    private static Cache CACHE;
    private static DataProvider<?> DATA_PROVIDER;
    private static Class<?> DATA_CLASS;
    private static Project CURRENT_PROJECT;
    private static Locale LOCALE;
    private static Proximity INSTANCE;

    @FXML private HBox sidebarPanes;
    @FXML private VBox root;
    @FXML private OptionsPane options;
    @FXML private HBox content = new HBox();
    @FXML private DataEntryArea dataEntryArea;
    @FXML private PreviewPane previewPane;

    public Proximity() {
        if (INSTANCE != null) {
            throw new RuntimeException("Attempted to initialize Proximity multiple times!");
        }

        INSTANCE = this;
    }

    public static void init(Path workingDirectory, Path pluginDirectory) throws InitializationException {
        WORKING_DIRECTORY = workingDirectory;
        PLUGIN_DIRECTORY = pluginDirectory;

        Path logs = WORKING_DIRECTORY.resolve("logs");
        LOG = Log.create("Proximity", logs);

        Path cacheDirectory = WORKING_DIRECTORY.resolve(".tmp").resolve("cache");
        CACHE = new Cache(LOG, cacheDirectory);

        // Iterate over plugin files
        if (Files.exists(PLUGIN_DIRECTORY)) {
            ModuleFinder pluginFinder = ModuleFinder.of(PLUGIN_DIRECTORY);

            List<String> plugins = pluginFinder.findAll().stream()
                    .map(ModuleReference::descriptor)
                    .map(ModuleDescriptor::name)
                    .collect(Collectors.toList());

            Configuration pluginConfiguration = ModuleLayer.boot().configuration()
                    .resolve(pluginFinder, ModuleFinder.of(), plugins);

            MODULES = ModuleLayer
                    .boot()
                    .defineModulesWithOneLoader(pluginConfiguration, ClassLoader.getSystemClassLoader());
        }

        for (DataProvider<?> provider : getImplementations(DataProvider.class)) {
            if (DATA_CLASS == null) {
                DATA_PROVIDER = provider;
                DATA_CLASS = provider.getDataClass();
            } else if (DATA_CLASS != provider.getDataClass()) {
                throw new InitializationException("Only one data class can be supported at a time.");
            } else {
                DATA_PROVIDERS.add(provider);
            }
        }
    }

    @FXML public void initialize() {
        initialize(this.root);
    }

    private static void initialize(Pane pane) {
        for (Node node : pane.getChildren()) {
            if (node instanceof Initializable initializable) {
                initializable.initialize();
            }

            if (node instanceof Pane p) {
                initialize(p);
            }
        }
    }

    public static boolean isSidebarExpanded() {
        return activeCategory.get() != null;
    }

    public static SidebarPane getActiveSidebarPane() {
        return activeCategory.get();
    }

    public static void setActiveSidebarPane(SidebarPane sidebarPane) {
        activeCategory.setValue(sidebarPane);
    }

    public static OptionsPane getOptionsCategory() {
        return INSTANCE.options;
    }


    public static <T> Iterable<T> getImplementations(Class<T> serviceClass) {
        List<T> services = new ArrayList<>();

        ServiceLoader.load(ModuleLayer.boot(), serviceClass).forEach(services::add);

        if (MODULES != null) {
            ServiceLoader.load(MODULES, serviceClass).forEach(services::add);
        }

        return services;
    }

    public static Plugin load(URL jar) throws IOException, PluginInstantiationException {
        return switch (jar.getProtocol()) {
            case "file" -> Plugin.fromJar(jar);
            case "http", "https" -> Plugin.fromJar(CACHE.cache(jar));
            default -> throw new IllegalStateException("Unexpected protocol: " + jar.getProtocol());
        };
    }

    /**
     * Saves the remote URL to the local cache and returns the URL referencing the saved file.
     *
     * @param url some remote resource
     * @return a URL to a local file
     */
    public static URL cache(URL url) throws IOException {
        return CACHE.cache(url);
    }

    public static <D extends RenderJob> DataProvider<D> getDataProvider() {
        //noinspection unchecked
        return (DataProvider<D>) DATA_PROVIDER;
    }

    public static Iterable<Template> templates() {
        return getImplementations(Template.class);
    }

    public static boolean isPaused() {
        return PAUSED.get();
    }

    public static void pause() {
        PAUSED.set(true);
    }

    public static void resume() {
        PAUSED.set(false);
    }

    public static void setPauseText(String text) {
        INSTANCE.dataEntryArea.setPauseText(text);
    }

    public static Log deriveLogger(String name) {
        return LOG.derive(name);
    }

    public static void print(String message, Object... args) {
        LOG.print(message, args);
    }

    public static void print(Throwable error) {
        LOG.print(error);
    }

    public static void print(String message, Throwable error) {
        LOG.print(message, error);
    }

    public static void write(String message, Object... args) {
        LOG.write(message, args);
    }

    public static void write(Throwable error) {
        LOG.write(error);
    }

    public static void write(String message, Throwable error) {
        LOG.write(message, error);
    }

    public static Project getCurrentProject() {
        if (CURRENT_PROJECT == null) {
            CURRENT_PROJECT = new Project();
        }

        return CURRENT_PROJECT;
    }

    public static void render(DataWidget.Entry<?> entry) {
        INSTANCE.previewPane.render(entry);
    }

    public static void rerender(DataWidget.Entry<?> entry) {
        INSTANCE.previewPane.rerender(entry);
    }

    public static Template<?> getTemplate(String name) {
        for (Template<?> template : templates()) {
            if (template.getName().equalsIgnoreCase(name)) {
                return template;
            }
        }

        print(String.format("Could not find template '%s'", name));

        return null;
    }

    public static <D extends RenderJob> void select(DataWidget.Entry<D> entry) {
        INSTANCE.previewPane.render(entry);
        INSTANCE.options.select(entry);
        SELECTION_MANAGER.select(entry);
    }

    public static void select(DataWidget<?> widget) {
        for (DataRow<?> row : INSTANCE.dataEntryArea.rows()) {
            if (row.represents(widget)) {
                SELECTION_MANAGER.select(row);
                break;
            }
        }
    }

    public static String getDataProviderPluginID() {
        return DATA_PROVIDER.getId();
    }

    public static void clearPreview() {
        INSTANCE.previewPane.clear();
    }

    public static ObservableValue<Boolean> getPausedProperty() {
        return PAUSED;
    }

    public static <D extends RenderJob> void select(DataRow<D> row) {
        SELECTION_MANAGER.select(row);
    }

    public static Log log() {
        return LOG;
    }

    public static boolean isSelected(DataWidget.Entry<?> entry) {
        return SELECTION_MANAGER.isSelected(entry);
    }

    public static void add(SidebarPane sidebarPane) {
        INSTANCE.sidebarPanes.getChildren().add(sidebarPane);
    }

    public static void add(List<DataWidget<?>> rows) {
        INSTANCE.dataEntryArea.add(rows, false);
    }
}