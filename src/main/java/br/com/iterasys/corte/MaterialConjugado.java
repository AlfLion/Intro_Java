package br.com.iterasys.corte;

/**
 * Dois materiais colados um no outro (ex.: Poron + cola transfer), cada um com sua
 * própria largura de rolo. O material mais largo é o que sobra quando os dois são
 * simplesmente abertos juntos pela largura menor.
 */
public class MaterialConjugado {

    private final Material materialMaisLargo;
    private final Material materialMaisEstreito;

    public MaterialConjugado(Material materialA, Material materialB) {
        if (materialA == null || materialB == null) {
            throw new IllegalArgumentException("os dois materiais devem ser informados");
        }
        if (materialA.getLarguraMm() == materialB.getLarguraMm()) {
            throw new IllegalArgumentException(
                    "os materiais têm a mesma largura (" + materialA.getLarguraMm()
                            + "mm); não há sobra a considerar, use um material único");
        }
        if (materialA.getLarguraMm() > materialB.getLarguraMm()) {
            this.materialMaisLargo = materialA;
            this.materialMaisEstreito = materialB;
        } else {
            this.materialMaisLargo = materialB;
            this.materialMaisEstreito = materialA;
        }
    }

    public Material getMaterialMaisLargo() {
        return materialMaisLargo;
    }

    public Material getMaterialMaisEstreito() {
        return materialMaisEstreito;
    }

    /** Largura da sobra do material mais largo quando os dois são abertos juntos. */
    public double getLarguraSobraMm() {
        return materialMaisLargo.getLarguraMm() - materialMaisEstreito.getLarguraMm();
    }

    /**
     * Dimensões da placa cheia obtida colando a largura de um material pela largura
     * do outro (rotacionando o mais estreito), sem gerar sobra.
     */
    public double getLarguraPlacaCheiaMm() {
        return materialMaisLargo.getLarguraMm();
    }

    public double getComprimentoPlacaCheiaMm() {
        return materialMaisEstreito.getLarguraMm();
    }
}
