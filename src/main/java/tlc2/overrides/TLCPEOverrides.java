package tlc2.overrides;

import tlc2.module.TLCPE;

import java.io.File;

@SuppressWarnings("unused")
public class TLCPEOverrides implements ITLCOverrides {
    static {
        String tlcOverrides = System.getProperty("tlc2.overrides.TLCOverrides", "tlc2.overrides.TLCOverrides");
        System.setProperty("tlc2.overrides.TLCOverrides",
                "tlc2.overrides.TLCPEOverrides" + File.pathSeparator + tlcOverrides);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Class[] get() {
        return new Class[]{ TLCPE.class };
    }
}
