package io.smithy.unison.codegen;

import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Context object for Unison code generation.
 * 
 * <p>This class implements Smithy's {@link CodegenContext} interface, providing
 * a central access point for all code generation dependencies.
 */
public final class UnisonContext 
        implements CodegenContext<UnisonSettings, UnisonWriter, UnisonIntegration> {
    
    private final Model model;
    private final UnisonSettings settings;
    private final SymbolProvider symbolProvider;
    private final FileManifest fileManifest;
    private final WriterDelegator<UnisonWriter> writerDelegator;
    private final List<UnisonIntegration> integrations;
    private final ServiceShape cachedService;
    
    private UnisonContext(Builder builder) {
        this.model = Objects.requireNonNull(builder.model, "model is required");
        this.settings = Objects.requireNonNull(builder.settings, "settings is required");
        this.symbolProvider = Objects.requireNonNull(builder.symbolProvider, "symbolProvider is required");
        this.fileManifest = Objects.requireNonNull(builder.fileManifest, "fileManifest is required");
        this.writerDelegator = Objects.requireNonNull(builder.writerDelegator, "writerDelegator is required");
        this.integrations = builder.integrations != null 
                ? Collections.unmodifiableList(new java.util.ArrayList<>(builder.integrations)) 
                : Collections.emptyList();
        this.cachedService = builder.service;
    }
    
    @Override
    public Model model() {
        return model;
    }
    
    @Override
    public UnisonSettings settings() {
        return settings;
    }
    
    @Override
    public SymbolProvider symbolProvider() {
        return symbolProvider;
    }
    
    @Override
    public FileManifest fileManifest() {
        return fileManifest;
    }
    
    @Override
    public WriterDelegator<UnisonWriter> writerDelegator() {
        return writerDelegator;
    }
    
    @Override
    public List<UnisonIntegration> integrations() {
        return integrations;
    }
    
    /**
     * Gets the service shape being generated.
     */
    public ServiceShape serviceShape() {
        if (cachedService != null) {
            return cachedService;
        }
        return model.getShape(settings.service())
                .filter(shape -> shape instanceof ServiceShape)
                .map(shape -> (ServiceShape) shape)
                .orElse(null);
    }
    
    /**
     * Gets the service shape ID being generated.
     */
    public ShapeId service() {
        return settings.service();
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static final class Builder {
        private Model model;
        private UnisonSettings settings;
        private SymbolProvider symbolProvider;
        private FileManifest fileManifest;
        private WriterDelegator<UnisonWriter> writerDelegator;
        private List<UnisonIntegration> integrations;
        private ServiceShape service;
        
        private Builder() {}
        
        public Builder model(Model model) {
            this.model = model;
            return this;
        }
        
        public Builder settings(UnisonSettings settings) {
            this.settings = settings;
            return this;
        }
        
        public Builder symbolProvider(SymbolProvider symbolProvider) {
            this.symbolProvider = symbolProvider;
            return this;
        }
        
        public Builder fileManifest(FileManifest fileManifest) {
            this.fileManifest = fileManifest;
            return this;
        }
        
        public Builder writerDelegator(WriterDelegator<UnisonWriter> writerDelegator) {
            this.writerDelegator = writerDelegator;
            return this;
        }
        
        public Builder integrations(List<UnisonIntegration> integrations) {
            this.integrations = integrations;
            return this;
        }
        
        public Builder service(ServiceShape service) {
            this.service = service;
            return this;
        }
        
        public UnisonContext build() {
            return new UnisonContext(this);
        }
    }
}
