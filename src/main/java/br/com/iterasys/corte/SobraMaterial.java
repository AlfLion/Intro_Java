package br.com.iterasys.corte;

/**
 * Sobra do material mais largo que fica sem colagem quando os dois materiais são
 * abertos juntos pela largura do material mais estreito.
 */
public record SobraMaterial(Material material, double larguraMm, double comprimentoMm) {
}
