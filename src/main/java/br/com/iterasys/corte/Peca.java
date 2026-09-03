package br.com.iterasys.corte;

/**
 * Peça demandada em uma ordem de serviço: suas dimensões e a quantidade necessária.
 */
public class Peca {

    private final String nome;
    private final double larguraMm;
    private final double comprimentoMm;
    private final int quantidade;

    public Peca(String nome, double larguraMm, double comprimentoMm, int quantidade) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome da peça não pode ser vazio");
        }
        if (larguraMm <= 0 || comprimentoMm <= 0) {
            throw new IllegalArgumentException("dimensões da peça devem ser positivas");
        }
        if (quantidade <= 0) {
            throw new IllegalArgumentException("quantidade deve ser positiva");
        }
        this.nome = nome;
        this.larguraMm = larguraMm;
        this.comprimentoMm = comprimentoMm;
        this.quantidade = quantidade;
    }

    public String getNome() {
        return nome;
    }

    public double getLarguraMm() {
        return larguraMm;
    }

    public double getComprimentoMm() {
        return comprimentoMm;
    }

    public int getQuantidade() {
        return quantidade;
    }

    public Peca comQuantidade(int novaQuantidade) {
        return new Peca(nome, larguraMm, comprimentoMm, novaQuantidade);
    }

    /** A mesma peça rotacionada 90° (largura e comprimento trocados) — nunca espelhada. */
    public Peca girada() {
        return new Peca(nome, comprimentoMm, larguraMm, quantidade);
    }

    public boolean isQuadrada() {
        return larguraMm == comprimentoMm;
    }
}
