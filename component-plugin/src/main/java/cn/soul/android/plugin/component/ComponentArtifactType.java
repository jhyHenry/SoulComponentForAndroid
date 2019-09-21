package cn.soul.android.plugin.component;

import com.android.build.api.artifact.ArtifactType;

/**
 * @author panxinghai
 * <p>
 * date : 2019-07-17 16:34
 */
public enum ComponentArtifactType implements ArtifactType {
    COMPONENT_AAR,
    COMPONENT_AAR_MAIN_JAR,
    COMPONENT_AAR_LIBS_DIR,
    COMPONENT_R_TXT,
    COMPONENT_NOT_NAMESPACE_R_CLASS_SOURCES,
}
