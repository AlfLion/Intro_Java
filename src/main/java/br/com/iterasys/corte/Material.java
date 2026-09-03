package br.com.iterasys.corte;

/**
 * Representa um material em rolo (ex.: Poron, cola transfer), com uma largura fixa
 * e comprimento considerado ilimitado (aberto conforme a necessidade).
 */
public class Material {

    private final String nome;
    private final double larguraMm;

    public Material(String nome, double larguraMm) {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("nome do material não pode ser vazio");
        }
        if (larguraMm <= 0) {
            throw new IllegalArgumentException("larguraMm deve ser positiva");
        }
        this.nome = nome;
        this.larguraMm = larguraMm;
    }

    public String getNome() {
        return nome;
    }

    public double getLarguraMm() {
        return larguraMm;
    }

    @Override
    public String toString() {
        return nome + " (" + larguraMm + "mm)";
    }
}
