package xyz.kyngs.librepremium.common.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import xyz.kyngs.librepremium.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librepremium.common.config.key.ConfigurationKey;
import xyz.kyngs.librepremium.common.config.migrate.ConfigurationMigrator;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;

public class ConfigurateConfiguration {

    private final ConfigurateHelper helper;
    private final boolean newlyCreated;
    private final HoconConfigurationLoader loader;

    public ConfigurateConfiguration(File dataFolder, String name, Class<?> defaultKeys, String comment, ConfigurationMigrator... migrators) throws IOException, CorruptedConfigurationException {
        var revision = migrators.length;
        var file = new File(dataFolder, name);

        if (!file.exists()) {
            newlyCreated = true;
            if (!file.createNewFile()) throw new IOException("Could not create configuration file!");
        } else newlyCreated = false;

        var refHelper = new ConfigurateHelper(CommentedConfigurationNode.root()
                .comment(comment)
        );

        try {
            for (Field field : defaultKeys.getFields()) {
                if (field.getType() != ConfigurationKey.class) continue;
                refHelper.setDefault((ConfigurationKey<?>) field.get(null));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        var ref = refHelper.configuration();

        ref.node("revision")
                .comment("The config revision number. !!DO NOT TOUCH THIS!!")
                .set(revision);

        var builder = HoconConfigurationLoader.builder()
                .defaultOptions(
                        ConfigurationOptions
                                .defaults()
                                .header(ref.comment())
                )
                .file(file)
                .emitComments(true)
                .prettyPrinting(true);


        loader = builder.build();

        try {
            helper = new ConfigurateHelper(loader.load()
                    .mergeFrom(ref));
        } catch (ConfigurateException e) {
            throw new CorruptedConfigurationException(e);
        }

        var presentRevision = helper.getInt("revision");

        if (presentRevision < revision) {
            for (int i = presentRevision; i < revision; i++) {
                migrators[i].migrate(helper);
            }
        }

        helper.set("revision", revision);

        save();
    }

    public ConfigurateHelper getHelper() {
        return helper;
    }

    public boolean isNewlyCreated() {
        return newlyCreated;
    }

    public void save() throws IOException {
        loader.save(helper.configuration());
    }


}
