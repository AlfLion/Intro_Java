package br.com.iterasys.corte;

/**
 * Decide, para uma peça que demanda um material conjugado, quantas placas cheias
 * (largura maior x largura menor, sem sobra) usar antes de completar o restante da
 * quantidade abrindo os dois materiais juntos pela largura menor — o que gera sobra
 * do material mais largo.
 */
public final class CalculadoraMaterialConjugado {

    private CalculadoraMaterialConjugado() {
    }

    public static PlanoConjugado calcular(MaterialConjugado material, Peca peca) {
        double larguraPlaca = material.getLarguraPlacaCheiaMm();
        double comprimentoPlaca = material.getComprimentoPlacaCheiaMm();

        int capacidadePorPlacaCheia = CalculadoraNesting.calcularCapacidadeEmArea(larguraPlaca, comprimentoPlaca, peca);
        if (capacidadePorPlacaCheia <= 0) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' não cabe nem em uma placa cheia de "
                            + larguraPlaca + "x" + comprimentoPlaca + "mm");
        }

        int placasCheias = peca.getQuantidade() / capacidadePorPlacaCheia;
        int pecasRestantes = peca.getQuantidade() % capacidadePorPlacaCheia;

        if (pecasRestantes == 0) {
            return new PlanoConjugado(placasCheias, larguraPlaca, comprimentoPlaca, null, null);
        }

        Peca pecaRestante = peca.comQuantidade(pecasRestantes);
        double larguraEstreita = material.getMaterialMaisEstreito().getLarguraMm();
        NestingResultado nestingTira = CalculadoraNesting.calcularAberturaNecessaria(larguraEstreita, pecaRestante);

        TiraComplementar tira = new TiraComplementar(larguraEstreita, nestingTira.comprimentoNecessarioMm(), pecasRestantes);
        SobraMaterial sobra = new SobraMaterial(
                material.getMaterialMaisLargo(),
                material.getLarguraSobraMm(),
                nestingTira.comprimentoNecessarioMm());

        return new PlanoConjugado(placasCheias, larguraPlaca, comprimentoPlaca, tira, sobra);
    }
}
