package br.com.iterasys.corte;

/**
 * Plano de corte para uma peça que demanda um material conjugado: quantas placas
 * cheias (sem sobra) são necessárias, e, se a quantidade não fechar em placas
 * cheias, a tira complementar aberta e a sobra do material mais largo que ela gera.
 * {@code placaGirada} indica se a peça precisa ser cortada rotacionada 90° (nunca
 * espelhada) dentro da placa cheia.
 */
public record PlanoConjugado(int placasCheias,
                              double larguraPlacaCheiaMm,
                              double comprimentoPlacaCheiaMm,
                              boolean placaGirada,
                              TiraComplementar tiraComplementar,
                              SobraMaterial sobra) {

    public boolean temTiraComplementar() {
        return tiraComplementar != null;
    }

    /** Quanto do rolo do material mais largo (ex.: Poron) este plano consome, em mm. */
    public double consumoMaterialMaisLargoMm() {
        return placasCheias * comprimentoPlacaCheiaMm + comprimentoTiraMm();
    }

    /** Quanto do rolo do material mais estreito (ex.: cola transfer) este plano consome, em mm. */
    public double consumoMaterialMaisEstreitoMm() {
        return placasCheias * larguraPlacaCheiaMm + comprimentoTiraMm();
    }

    private double comprimentoTiraMm() {
        return temTiraComplementar() ? tiraComplementar.comprimentoMm() : 0;
    }
}
