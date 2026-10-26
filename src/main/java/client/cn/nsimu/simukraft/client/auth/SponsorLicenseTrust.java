package client.cn.nsimu.simukraft.client.auth;

/**
 * 官网 License 签名公钥信任锚。轮换时先发布同时信任新旧 key 的客户端，
 * 再切换服务端签名 key；不得从 License 响应中无条件信任任意公钥。
 */
final class SponsorLicenseTrust {
    private static final String KEY_ID = "nsuk-rs256-1";
    private static final String MODULUS =
            "t-K8EzdkbpXWumVS2eC9kwr700W-SVHmEw3bu7dw2fdD9fiX4FM1Zvpaa7f0NKJvHMOEhnkNuN9c-HT8v3S7NGIyXeY9IAOcRO6abuLtyfYvqsjQ8k-uFf1cMn_Duc4bBsTnou8luoAaEDwN35TkKnXlZtyQQyoqonaTOqpIjZJUr00RLd9GKwAMpj9zqpETgILl5qfjFoWdIpnT5KfbdUd1zp75i_8RD_Fzqn3E2060QgVxngWqZUYSMqpqC4rdQuMS0sR7lEfYgwrzMsj544JgLIe94r63JEqxQyQk1ksGrUakeCIRUNJMHAl-zfJoPWplJAT0TR3DhvsicyQEvw";
    private static final String EXPONENT = "AQAB";

    private SponsorLicenseTrust() {
    }

    static boolean isTrusted(String keyId, String modulus, String exponent) {
        return KEY_ID.equals(keyId) && MODULUS.equals(modulus) && EXPONENT.equals(exponent);
    }
}
