package br.com.iterasys.corte;

/**
 * Encaixe (nesting) simples em grade: peças alinhadas lado a lado ao longo da largura
 * disponível, em fileiras empilhadas ao longo do comprimento. Testa a peça na orientação
 * original e rotacionada 90° (nunca espelhada) e fica com a que gasta menos material.
 */
public final class CalculadoraNesting {

    private CalculadoraNesting() {
    }

    /**
     * Quanto comprimento é preciso abrir do material para encaixar toda a
     * quantidade demandada da peça, dada uma largura disponível.
     */
    public static NestingResultado calcularAberturaNecessaria(double larguraDisponivelMm, Peca peca) {
        NestingResultado semGiro = tentarAbertura(larguraDisponivelMm, peca, false);
        NestingResultado comGiro = peca.isQuadrada() ? null : tentarAbertura(larguraDisponivelMm, peca.girada(), true);

        if (semGiro == null && comGiro == null) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' (" + peca.getLarguraMm() + "x" + peca.getComprimentoMm()
                            + "mm) não cabe na largura disponível de " + larguraDisponivelMm + "mm, girada ou não");
        }
        if (semGiro == null) {
            return comGiro;
        }
        if (comGiro == null) {
            return semGiro;
        }
        return comGiro.comprimentoNecessarioMm() < semGiro.comprimentoNecessarioMm() ? comGiro : semGiro;
    }

    /**
     * Quantas unidades da peça cabem em uma área limitada tanto em largura quanto em
     * comprimento (ex.: uma placa de tamanho fixo).
     */
    public static EncaixeEmArea calcularMelhorEncaixeEmArea(double larguraDisponivelMm, double comprimentoDisponivelMm, Peca peca) {
        EncaixeEmArea semGiro = encaixarEmArea(larguraDisponivelMm, comprimentoDisponivelMm, peca, false);
        if (peca.isQuadrada()) {
            return semGiro;
        }
        EncaixeEmArea comGiro = encaixarEmArea(larguraDisponivelMm, comprimentoDisponivelMm, peca.girada(), true);
        return comGiro.capacidadeTotal() > semGiro.capacidadeTotal() ? comGiro : semGiro;
    }

    /**
     * Melhor orientação por TAXA de aproveitamento (peças por mm de fileira aberta),
     * útil para comparar o custo por peça de um modo de corte sem depender de uma
     * quantidade concreta. Diferente de {@link #calcularAberturaNecessaria}, que
     * minimiza o comprimento total para uma quantidade específica — o que não é a
     * mesma coisa: uma orientação pode precisar de uma fileira mais curta mas caber
     * bem menos peças nela, sendo pior na taxa apesar de "menor".
     */
    public static NestingResultado calcularMelhorTaxaPorFileira(double larguraDisponivelMm, Peca peca) {
        NestingResultado semGiro = umaFileira(larguraDisponivelMm, peca, false);
        NestingResultado comGiro = peca.isQuadrada() ? null : umaFileira(larguraDisponivelMm, peca.girada(), true);

        if (semGiro == null && comGiro == null) {
            throw new IllegalArgumentException(
                    "a peça '" + peca.getNome() + "' (" + peca.getLarguraMm() + "x" + peca.getComprimentoMm()
                            + "mm) não cabe na largura disponível de " + larguraDisponivelMm + "mm, girada ou não");
        }
        if (semGiro == null) {
            return comGiro;
        }
        if (comGiro == null) {
            return semGiro;
        }
        double taxaSemGiro = semGiro.pecasPorFileira() / semGiro.comprimentoNecessarioMm();
        double taxaComGiro = comGiro.pecasPorFileira() / comGiro.comprimentoNecessarioMm();
        return taxaComGiro > taxaSemGiro ? comGiro : semGiro;
    }

    private static NestingResultado umaFileira(double larguraDisponivelMm, Peca peca, boolean girada) {
        int pecasPorFileira = pecasPorFileiraOuZero(larguraDisponivelMm, peca);
        if (pecasPorFileira <= 0) {
            return null;
        }
        return new NestingResultado(pecasPorFileira, 1, peca.getComprimentoMm(), girada);
    }

    private static NestingResultado tentarAbertura(double larguraDisponivelMm, Peca peca, boolean girada) {
        int pecasPorFileira = pecasPorFileiraOuZero(larguraDisponivelMm, peca);
        if (pecasPorFileira <= 0) {
            return null;
        }
        int fileiras = (int) Math.ceil((double) peca.getQuantidade() / pecasPorFileira);
        double comprimentoNecessarioMm = fileiras * peca.getComprimentoMm();
        return new NestingResultado(pecasPorFileira, fileiras, comprimentoNecessarioMm, girada);
    }

    private static EncaixeEmArea encaixarEmArea(double larguraDisponivelMm, double comprimentoDisponivelMm, Peca peca, boolean girada) {
        int pecasPorFileira = pecasPorFileiraOuZero(larguraDisponivelMm, peca);
        int fileiras = pecasPorFileira <= 0 ? 0 : (int) Math.floor(comprimentoDisponivelMm / peca.getComprimentoMm());
        return new EncaixeEmArea(pecasPorFileira, fileiras, pecasPorFileira * fileiras, girada);
    }

    /**
     * Igual a dividir a largura disponível pela largura da peça, mas devolve 0 em vez
     * de lançar exceção quando a peça não cabe — usado quando o chamador precisa
     * avaliar se uma orientação é viável antes de decidir se a usa.
     */
    public static int pecasPorFileiraOuZero(double larguraDisponivelMm, Peca peca) {
        return (int) Math.floor(larguraDisponivelMm / peca.getLarguraMm());
    }
}
