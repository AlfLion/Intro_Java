package br.com.iterasys.corte;

/**
 * Decide, para uma peça que demanda um material conjugado, entre usar placas cheias
 * (largura maior x largura menor, sem sobra) ou abrir os dois materiais juntos pela
 * largura menor (o que gera sobra do material mais largo) — sempre pelo critério de
 * gastar a menor quantidade possível do material mais largo. Placa cheia só é usada
 * quando ela de fato consome menos material por peça do que a tira; caso contrário,
 * mesmo aceitando a sobra, a tira é a opção mais econômica.
 */
public final class CalculadoraMaterialConjugado {

    private CalculadoraMaterialConjugado() {
    }

    public static PlanoConjugado calcular(MaterialConjugado material, Peca peca) {
        double larguraPlaca = material.getLarguraPlacaCheiaMm();
        double profundidadePlaca = material.getComprimentoPlacaCheiaMm();

        int pecasPorFileiraPlaca = CalculadoraNesting.pecasPorFileiraOuZero(larguraPlaca, peca);
        int fileirasPlaca = pecasPorFileiraPlaca == 0 ? 0 : (int) Math.floor(profundidadePlaca / peca.getComprimentoMm());
        int capacidadePlaca = pecasPorFileiraPlaca * fileirasPlaca;

        int pecasPorFileiraTira = CalculadoraNesting.pecasPorFileiraOuZero(profundidadePlaca, peca);

        if (capacidadePlaca <= 0 && pecasPorFileiraTira <= 0) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' não cabe nem na placa cheia (" + larguraPlaca + "x"
                            + profundidadePlaca + "mm) nem na largura do material mais estreito (" + profundidadePlaca + "mm)");
        }

        boolean placaViavel = capacidadePlaca > 0;
        boolean tiraViavel = pecasPorFileiraTira > 0;
        boolean placaMaisEconomica = placaViavel
                && (!tiraViavel || custoPorPeca(profundidadePlaca, capacidadePlaca)
                        <= custoPorPeca(peca.getComprimentoMm(), pecasPorFileiraTira));

        if (!placaMaisEconomica) {
            return usarSomenteTira(material, peca, profundidadePlaca, larguraPlaca, peca.getQuantidade());
        }

        int placasCheias = peca.getQuantidade() / capacidadePlaca;
        int pecasRestantes = peca.getQuantidade() % capacidadePlaca;

        if (pecasRestantes == 0) {
            return new PlanoConjugado(placasCheias, larguraPlaca, profundidadePlaca, null, null);
        }

        // para o restante, decide entre completar com mais uma placa cheia inteira
        // (custo fixo de uma profundidade de placa) ou abrir uma tira só do necessário
        double custoPlacaExtra = profundidadePlaca;
        double custoTiraRestante = tiraViavel
                ? CalculadoraNesting.calcularAberturaNecessaria(profundidadePlaca, peca.comQuantidade(pecasRestantes)).comprimentoNecessarioMm()
                : Double.POSITIVE_INFINITY;

        if (custoPlacaExtra <= custoTiraRestante) {
            return new PlanoConjugado(placasCheias + 1, larguraPlaca, profundidadePlaca, null, null);
        }

        return comPlacasETira(material, peca, profundidadePlaca, larguraPlaca, placasCheias, pecasRestantes);
    }

    private static PlanoConjugado usarSomenteTira(MaterialConjugado material, Peca peca, double profundidadePlaca,
                                                    double larguraPlaca, int quantidade) {
        return comPlacasETira(material, peca, profundidadePlaca, larguraPlaca, 0, quantidade);
    }

    private static PlanoConjugado comPlacasETira(MaterialConjugado material, Peca peca, double profundidadePlaca,
                                                   double larguraPlaca, int placasCheias, int quantidadeNaTira) {
        Peca pecaDaTira = peca.comQuantidade(quantidadeNaTira);
        NestingResultado nestingTira = CalculadoraNesting.calcularAberturaNecessaria(profundidadePlaca, pecaDaTira);

        TiraComplementar tira = new TiraComplementar(profundidadePlaca, nestingTira.comprimentoNecessarioMm(), quantidadeNaTira);
        SobraMaterial sobra = new SobraMaterial(
                material.getMaterialMaisLargo(),
                material.getLarguraSobraMm(),
                nestingTira.comprimentoNecessarioMm());

        return new PlanoConjugado(placasCheias, larguraPlaca, profundidadePlaca, tira, sobra);
    }

    private static double custoPorPeca(double comprimentoConsumidoMm, int pecasObtidas) {
        return comprimentoConsumidoMm / pecasObtidas;
    }
}
