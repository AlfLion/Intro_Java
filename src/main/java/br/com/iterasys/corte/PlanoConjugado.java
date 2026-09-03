package br.com.iterasys.corte;

/**
 * Plano de corte para uma peça que demanda um material conjugado: quantas placas
 * cheias (sem sobra) são necessárias, e, se a quantidade não fechar em placas
 * cheias, a tira complementar aberta e a sobra do material mais largo que ela gera.
 */
public record PlanoConjugado(int placasCheias,
                              double larguraPlacaCheiaMm,
                              double comprimentoPlacaCheiaMm,
                              TiraComplementar tiraComplementar,
                              SobraMaterial sobra) {

    public boolean temTiraComplementar() {
        return tiraComplementar != null;
    }
}
