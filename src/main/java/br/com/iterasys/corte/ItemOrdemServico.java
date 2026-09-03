package br.com.iterasys.corte;

/**
 * Um item de uma ordem de serviço: uma peça demandada e o material que ela precisa
 * — único, ou conjugado (dois materiais colados).
 */
public final class ItemOrdemServico {

    private final Peca peca;
    private final Material materialUnico;
    private final MaterialConjugado materialConjugado;

    private ItemOrdemServico(Peca peca, Material materialUnico, MaterialConjugado materialConjugado) {
        this.peca = peca;
        this.materialUnico = materialUnico;
        this.materialConjugado = materialConjugado;
    }

    public static ItemOrdemServico deMaterialUnico(Peca peca, Material material) {
        if (material == null) {
            throw new IllegalArgumentException("material não pode ser nulo");
        }
        return new ItemOrdemServico(peca, material, null);
    }

    public static ItemOrdemServico deMaterialConjugado(Peca peca, MaterialConjugado material) {
        if (material == null) {
            throw new IllegalArgumentException("material conjugado não pode ser nulo");
        }
        return new ItemOrdemServico(peca, null, material);
    }

    public Peca getPeca() {
        return peca;
    }

    public boolean isMaterialConjugado() {
        return materialConjugado != null;
    }

    public Material getMaterialUnico() {
        return materialUnico;
    }

    public MaterialConjugado getMaterialConjugado() {
        return materialConjugado;
    }
}
