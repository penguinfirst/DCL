import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ListToArrayAsArg {
    public static void foo(String[] args) { }

    public static void main(String[] args) throws Exception {
        Method m = ListToArrayAsArg.class.getMethod("foo", String[].class);
        List<String> list = new ArrayList<String>();
        list.add("hello");
        m.invoke(null, new Object[]{list.toArray(new String[0])});
        PTAAssert.reachable("<ListToArrayAsArg: void foo(java.lang.String[])>");
    }
}
