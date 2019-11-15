package cn.soul.android.component.node;

/**
 * @author panxinghai
 * <p>
 * date : 2019-11-15 18:37
 */
public class UnspecifiedNode extends RouterNode{
    public UnspecifiedNode(String path, Class<?> target) {
        super(path, target);
    }

    @Override
    public NodeType getType() {
        return null;
    }
}
