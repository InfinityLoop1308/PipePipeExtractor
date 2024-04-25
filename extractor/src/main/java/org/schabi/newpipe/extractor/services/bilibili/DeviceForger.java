package org.schabi.newpipe.extractor.services.bilibili;

import java.util.Random;

import javax.annotation.Nonnull;

public class DeviceForger {

    static public class Device {
        private String userAgent;

        private String webGlVersion;
        private String webGlVersionBase64;

        private String webGLRendererInfo;
        private String webGLRendererInfoBase64;

        public String getUserAgent() {
            return userAgent;
        }

        void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public String getWebGlVersion() {
            return webGlVersion;
        }

        public String getWebGlVersionBase64() {
            return webGlVersionBase64;
        }

        void setWebGlVersion(String webGlVersion) {
            this.webGlVersion = webGlVersion;
            this.webGlVersionBase64 = utils.encodeToBase64SubString(webGlVersion);
        }

        public String getWebGLRendererInfo() {
            return webGLRendererInfo;
        }

        public String getWebGLRendererInfoBase64() {
            return webGLRendererInfoBase64;
        }

        void setWebGLRendererInfo(String webGLRendererInfo) {
            this.webGLRendererInfo = webGLRendererInfo;
            this.webGLRendererInfoBase64 = utils.encodeToBase64SubString(webGLRendererInfo);
        }

        public Device(String userAgent, String webGlVersion, String webGLRendererInfo) {
            this.userAgent = userAgent;
            this.webGlVersion = webGlVersion;
            this.webGlVersionBase64 = utils.encodeToBase64SubString(webGlVersion);
            this.webGLRendererInfo = webGLRendererInfo;
            this.webGLRendererInfoBase64 = utils.encodeToBase64SubString(webGLRendererInfo);
        }

        public String info() {
            return "{"
                    + "UserAgent: " + getUserAgent()
                    + ", WebGlVersion: " + getWebGlVersion()
                    + ", WebGLRendererInfo: " + getWebGLRendererInfo()
                    + "}";
        }
    }

    static class GraphicCard {
        private final String vendor;
        private final String model;

        public GraphicCard(String vendor, String model) {
            this.vendor = vendor;
            this.model = model;
        }

        public String getVendor() {
            return vendor;
        }

        public String getModel() {
            return model;
        }
    }

    final static String UserAgentTemplate = "Mozilla/5.0 (%s) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/%d.0.0.0 Safari/537.36";
    final static String DefaultChromiumWebGlVersion = "WebGL 1.0 (OpenGL ES 2.0 Chromium)";
    final static String ChromiumAngleRendererInfoTemplate = "ANGLE (%s, %s Direct3D11 vs_5_0 ps_5_0, D3D11)Google Inc. (%s)";

    static String buildUserAgent(String platform, int version) {
        return String.format(UserAgentTemplate, platform, version);
    }

    static String buildChromiumAngleRendererInfo(String vendor, String gpuWithApi) {
        return String.format(ChromiumAngleRendererInfoTemplate, vendor, gpuWithApi, vendor);
    }

    static GraphicCard randomCard(Random random) {
        GraphicCard[] cards = {
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 660 (0x000011C0)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 745 (0x00001382)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 750 (0x00001381)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 760 (0x00001187)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 950 (0x00001402)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 960 (0x00001401)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 970 (0x000013C2)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1050 (0x00001C83)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1050 (0x00001C8D)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1070 (0x00001BA1)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce GTX 1650 (0x00001F0A)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 2060 (0x00001F03)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 2060 (0x00001F08)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 2060 (0x00001F11)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3050 (0x00002582)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3050 (0x00002584)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x00002487)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x000024C7)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x00002503)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 (0x00002504)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x00002414)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x00002486)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3060 Ti (0x00002489)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3070 (0x00002484)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3070 (0x00002488)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 3080 (0x00002216)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4060 (0x00002882)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4070 (0x00002709)"),
                new GraphicCard("NVIDIA", "NVIDIA GeForce RTX 4070 (0x00002786)"),
                new GraphicCard("NVIDIA", "NVIDIA Quadro K2200 (0x000013BA)"),
                new GraphicCard("NVIDIA", "NVIDIA Quadro P2000 (0x00001C30)"),
                new GraphicCard("NVIDIA", "NVIDIA Quadro P400 (0x00001CB3)"),
                new GraphicCard("NVIDIA", "NVIDIA Quadro P5000 (0x00001BB0)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics (0x00000152)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics (0x0000016A)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics (0x00000402)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics (0x00000F31)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 520 (0x00001916)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 620 (0x00005916)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 630 (0x00005912)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 4400 (0x0000041E"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 4600 (0x00000412"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 4600 (0x00000416"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 5300 (0x0000161E)"),
                new GraphicCard("Intel", "Intel(R) HD Graphics 5500 (0x00001616)"),
                new GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x000046A8)"),
                new GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x00004908)"),
                new GraphicCard("Intel", "Intel(R) Iris(R) Xe Graphics (0x00009A40)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00004626)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00004628)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x0000468B)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00008A56)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009A60)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009A68)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009B21)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009B41)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics (0x00009BA4)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 600 (0x00003185)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 610 (0x00009BA8)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 620 (0x00003EA0)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00003E92)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00003E98)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 630 (0x00009BC8)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 730 (0x00004682)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 730 (0x00004692)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 730 (0x00004C8B)"),
                new GraphicCard("Intel", "Intel(R) UHD Graphics 770 (0x00004680)"),
                new GraphicCard("AMD", "AMD Radeon 780M Graphics (0x00001900)"),
                new GraphicCard("AMD", "AMD Radeon RX 5500 XT (0x00007340)"),
                new GraphicCard("AMD", "AMD Radeon RX 5600 XT (0x0000731F)"),
                new GraphicCard("AMD", "AMD Radeon RX 6600 XT (0x000073FF)"),
                new GraphicCard("AMD", "AMD Radeon RX 6700 XT (0x000073DF)"),
                new GraphicCard("AMD", "AMD Radeon RX 6800 XT (0x000073BF)"),
                new GraphicCard("AMD", "AMD Radeon RX 6900 XT (0x000073BF)"),
                new GraphicCard("AMD", "AMD Radeon RX 7900 GRE (0x0000744C)"),
                new GraphicCard("AMD", "AMD Radeon(TM) Graphics (0x000015BF)"),
                new GraphicCard("AMD", "AMD Radeon(TM) Graphics (0x0000164E)"),
                new GraphicCard("AMD", "AMD Radeon(TM) RX Vega 10 Graphics (0x000015D8)"),
                new GraphicCard("Unknown", "Mi Pad 5 Adreno 640 GPU"),
                new GraphicCard("Unknown", "Qualcomm(R) Adreno(TM) 8cx Gen 3"),
        };
        return cards[random.nextInt(cards.length)];
    }

    static Device forgeDevice(Random random) {
        // currently we only forge device with
        // * Modern Chrome Browse (so User Agent are frozen)
        // * Windows 10/11 X64 Operate System
        int chromiumVersion = (random.nextInt(4)) + 122;
        GraphicCard graphicCard = randomCard(random);
        Device device = new Device(
                buildUserAgent("Windows NT 10.0; Win64; x64", chromiumVersion),
                DefaultChromiumWebGlVersion,
                buildChromiumAngleRendererInfo(graphicCard.getVendor(), graphicCard.getModel())
        );
        return device;
    }

    static private Device currentDevice = null;

    @Nonnull
    static public Device requireRandomDevice() {
        if (currentDevice == null) {
            regenerateRandomDevice();
        }
        return currentDevice;
    }

    static public void regenerateRandomDevice() {
        currentDevice = forgeDevice(new Random(System.currentTimeMillis()));
    }

}
