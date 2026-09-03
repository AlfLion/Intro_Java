package br.com.iterasys.corte;

/**
 * Decide, para uma peça que demanda um material conjugado, entre usar placas cheias
 * (largura maior x largura menor, sem sobra) ou abrir os dois materiais juntos pela
 * largura menor (o que gera sobra do material mais largo) — sempre pelo critério de
 * gastar a menor quantidade possível do material mais largo. Placa cheia só é usada
 * quando ela de fato consome menos material por peça do que a tira; caso contrário,
 * mesmo aceitando a sobra, a tira é a opção mais econômica. Em cada modo, a peça é
 * testada rotacionada 90° (nunca espelhada) e a orientação que aproveita mais é a
 * escolhida.
 */
public final class CalculadoraMaterialConjugado {

    private CalculadoraMaterialConjugado() {
    }

    public static PlanoConjugado calcular(MaterialConjugado material, Peca peca) {
        double larguraPlaca = material.getLarguraPlacaCheiaMm();
        double profundidadePlaca = material.getComprimentoPlacaCheiaMm();

        EncaixeEmArea encaixePlaca = CalculadoraNesting.calcularMelhorEncaixeEmArea(larguraPlaca, profundidadePlaca, peca);
        int capacidadePlaca = encaixePlaca.capacidadeTotal();

        NestingResultado taxaTira = tentarMelhorTaxaTira(profundidadePlaca, peca);
        boolean tiraViavel = taxaTira != null;

        if (capacidadePlaca <= 0 && !tiraViavel) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' não cabe nem na placa cheia (" + larguraPlaca + "x"
                            + profundidadePlaca + "mm) nem na largura do material mais estreito (" + profundidadePlaca
                            + "mm), girada ou não");
        }

        boolean placaMaisEconomica = capacidadePlaca > 0
                && (!tiraViavel || custoPorPeca(profundidadePlaca, capacidadePlaca)
                        <= custoPorPeca(taxaTira.comprimentoNecessarioMm(), taxaTira.pecasPorFileira()));

        if (!placaMaisEconomica) {
            return comPlacasETira(material, peca, profundidadePlaca, larguraPlaca, 0, false, peca.getQuantidade());
        }

        int placasCheias = peca.getQuantidade() / capacidadePlaca;
        int pecasRestantes = peca.getQuantidade() % capacidadePlaca;

        if (pecasRestantes == 0) {
            return new PlanoConjugado(placasCheias, larguraPlaca, profundidadePlaca, encaixePlaca.pecaGirada(), null, null);
        }

        // para o restante, decide entre completar com mais uma placa cheia inteira
        // (custo fixo de uma profundidade de placa) ou abrir uma tira só do necessário
        double custoPlacaExtra = profundidadePlaca;
        NestingResultado tiraRestante = tiraViavel
                ? CalculadoraNesting.calcularAberturaNecessaria(profundidadePlaca, peca.comQuantidade(pecasRestantes))
                : null;
        double custoTiraRestante = tiraRestante != null ? tiraRestante.comprimentoNecessarioMm() : Double.POSITIVE_INFINITY;

        if (custoPlacaExtra <= custoTiraRestante) {
            return new PlanoConjugado(placasCheias + 1, larguraPlaca, profundidadePlaca, encaixePlaca.pecaGirada(), null, null);
        }

        return comPlacasETira(material, peca, profundidadePlaca, larguraPlaca, placasCheias, encaixePlaca.pecaGirada(), pecasRestantes);
    }

    private static NestingResultado tentarMelhorTaxaTira(double profundidadePlaca, Peca peca) {
        try {
            return CalculadoraNesting.calcularMelhorTaxaPorFileira(profundidadePlaca, peca);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static PlanoConjugado comPlacasETira(MaterialConjugado material, Peca peca, double profundidadePlaca,
                                                   double larguraPlaca, int placasCheias, boolean placaGirada, int quantidadeNaTira) {
        NestingResultado nestingTira = CalculadoraNesting.calcularAberturaNecessaria(profundidadePlaca, peca.comQuantidade(quantidadeNaTira));

        TiraComplementar tira = new TiraComplementar(profundidadePlaca, nestingTira.comprimentoNecessarioMm(), quantidadeNaTira, nestingTira.pecaGirada());
        SobraMaterial sobra = new SobraMaterial(
                material.getMaterialMaisLargo(),
                material.getLarguraSobraMm(),
                nestingTira.comprimentoNecessarioMm());

        return new PlanoConjugado(placasCheias, larguraPlaca, profundidadePlaca, placaGirada, tira, sobra);
    }

    private static double custoPorPeca(double comprimentoConsumidoMm, int pecasObtidas) {
        return comprimentoConsumidoMm / pecasObtidas;
    }
}
