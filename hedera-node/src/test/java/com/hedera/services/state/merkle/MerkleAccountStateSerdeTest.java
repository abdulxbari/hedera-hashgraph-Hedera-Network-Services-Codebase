package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.TokenAssociationMetadata;
import com.hedera.test.serde.SelfSerializableDataTest;
import com.hedera.test.utils.SeededPropertySource;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;

import java.util.Collections;

import static com.hedera.services.state.merkle.MerkleAccountState.RELEASE_0230_VERSION;
import static com.swirlds.common.CommonUtils.unhex;

public class MerkleAccountStateSerdeTest extends SelfSerializableDataTest<MerkleAccountState> {
	@Override
	protected Class<MerkleAccountState> getType() {
		return MerkleAccountState.class;
	}

	@Override
	protected void registerConstructables() throws ConstructableRegistryException {
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(EntityId.class, EntityId::new));
		ConstructableRegistry.registerConstructable(
				new ClassConstructorPair(FcTokenAllowanceId.class, FcTokenAllowanceId::new));
	}

	@Override
	protected int getNumTestCasesFor(int version) {
		return version == RELEASE_0230_VERSION ? num0240Forms : hexedForms.length - num0240Forms;
	}

	@Override
	protected byte[] getSerializedForm(final int version, final int testCaseNo) {
		return unhex(getHexedForm(version, testCaseNo));
	}

	@Override
	protected MerkleAccountState getExpectedObject(final int version, final int testCaseNo) {
		final var propertySource = SeededPropertySource.forSerdeTest(version, testCaseNo);
		if (version == RELEASE_0230_VERSION) {
			return new MerkleAccountState(
					propertySource.nextKey(),
					propertySource.nextUnsignedLong(),
					propertySource.nextUnsignedLong(),
					propertySource.nextUnsignedLong(),
					propertySource.nextString(100),
					propertySource.nextBoolean(),
					propertySource.nextBoolean(),
					propertySource.nextBoolean(),
					propertySource.nextEntityId(),
					propertySource.nextInt(),
					propertySource.nextUnsignedInt(),
					propertySource.nextByteString(36),
					propertySource.nextUnsignedInt(),
					// This migration relied on the fact that no 0.24.x production state ever included allowances
					Collections.emptyMap(),
					Collections.emptyMap(),
					Collections.emptySet());
		} else {
			final var seeded = new MerkleAccountState(
					propertySource.nextKey(),
					propertySource.nextUnsignedLong(),
					propertySource.nextUnsignedLong(),
					propertySource.nextUnsignedLong(),
					propertySource.nextString(100),
					propertySource.nextBoolean(),
					propertySource.nextBoolean(),
					propertySource.nextBoolean(),
					propertySource.nextEntityId(),
					propertySource.nextInt(),
					propertySource.nextUnsignedInt(),
					propertySource.nextByteString(36),
					propertySource.nextUnsignedInt(),
					// This migration relied on the fact that no 0.24.x production state ever included allowances
					propertySource.nextGrantedCryptoAllowances(10),
					propertySource.nextGrantedFungibleAllowances(10),
					propertySource.nextApprovedForAllAllowances(10));
			seeded.setTokenAssociationMetadata(new TokenAssociationMetadata(
					propertySource.nextUnsignedInt(), propertySource.nextUnsignedInt(), propertySource.nextPair()));
			return seeded;
		}
	}

	private static String getHexedForm(final int version, final int testCaseNo) {
		return version == RELEASE_0230_VERSION ? hexedForms[testCaseNo] : hexedForms[testCaseNo + num0240Forms];
	}

	private static final int num0240Forms = 5;
	private static final String[] hexedForms = {
			// The 0.24 forms
			"0100000000000000020000000000ed33e400000000000000185c2548b75e34082e7fd6390250ec85f25c2194fb17ca2662641392aeea16cb954fb8abb0c2a5106c6e1830fd4c79efa0000000645412211c7c042212385315792d433c46126a2b3b621166222405732467327e384b484d64144c2b495b2265640a7b4b125f7410593d616c464f6e6b1e603e0f581042795e6c16155f40370b371f3f146e220f1357210c693854176f3037756a6b1e553b0900010101f35ba643324efa37000000017d24b40e843d249f01a7d864fee3e95f5933e55f143342880000000000000000699678a30b0512690000002497a3a4ab886f682f120e1951b276c693cdb485b593cbc9143fc5ad098df7c9fd2f73acdc14c8dc43000000000000000000000000",
			"0100000000000000020000000000ef843a000000000000002479a3f6f4eb08dc8c35ace9cdd6751b3932f7bcb56a75aa5164ca4020890f574583be51846fdf4e079ad9d85c29fb744b11c53bd4761bb88d24b3f96d00000064227327405c0b5b6f5511551d3350474c517578280c3e4a7475020904064c67612a7c6e613a101e2b0b5d203f061075287874085a623b771d41373921717a5c103f38061c6a5f2c7d2a2c3c29234e235d2841166c5d19362c102a5f2b025a4d2f29257d1d01000101f35ba643324efa37000000013b91f2ff57c7166714421e8ee3d12f6c71e4ac7aba80b0e60000000000000000738734c6b81b1552000000249226400895bc803541b4faeb3207558c0768f90bc132b51fcf6058027e7a984acf0aed7b7f1b39a6000000000000000000000000",
			"0100000000000000020000000000ed33e40000000000000018759a1591ed3a228d48f79768661df08644c059dfe0b0bb401f8bc90e2bd9bd7e382b648720d5b1a7295a8e1cd30c1d710000006437516c0c0f421c48415f75634141555b7853022e03673a0b6d777d146f66512d322b3c2016722e257c1d1b2f2319531e69700c276b70201c4b27275b5d022c6507481272534b53652b52054a7110434737463c7b5b0a0e156f1b163c4c7c03475934225301000101f35ba643324efa37000000013bf9730eb87e92b242d91185bf21048f74b3e0a2df9aab8c00000000000000001b996e999edb21fe0000002452015b3850d86b5cb4c46925fde08adac1615770d7ca8f6ffb5d03deab090ee5abc797b122892443000000000000000000000000",
			"0100000000000000020000000000ed33e400000000000000181e202222d4ca95432d31457fe817ee7618c4993a3938dbc32191fa37dd9cacea4e1a2d8b4b6848381bbb0df9b697593c00000064326d2936023d27552a41572f1c217344110d1a37796a4c3e0f2b767b49210a12382312260949634e7d6f00295c490c793d163c6e60470a56242e2349136f5112021824775e016c2f65010d7e777a08081e6167482a4e7a74495d07780b2d5e4f7c392c1800000101f35ba643324efa37000000015adc9ab1e86a5a114a2122d4801bfa003a664bf91227c92c00000000000000002ef997ccf95ea8f200000024ddfbfdef3863bd121aa0541b9e1304f90139c6238914b2f701b60ecc12e286902cf9204e0f2f1a52000000000000000000000000",
			"0100000000000000020000000000ef843a00000000000000244953d500b42ff63812fec78be8d21d67dce01ecbf8e9185da4cd272bc02468b9d93e5744240e5fec3b820ec369c86dfd827f41ae78c794f1e3c50ae4000000643a0e424303365725485e3f6c627c051316233a31541e635432766307517b093a0a4561693f2f5048113e582511405c263c39763b2145087d156d37562e01060e0557054e61363129463f56057c0c0e504224381c1c381c6d4e38442c1c404c216212570101010101f35ba643324efa37000000013cf7bcb9cc73324618a01cf48aafbed3498e92b35a0452a4000000000000000047e50ae9b36e1950000000246bbf2218f045d8c351ce4def012f48636280108f2748404be60e427f0fddab831a368b5b79e1a38a000000000000000000000000",
			// The 0.25 forms
			"0100000000000000020000000000ecb1f000000000000000c20000000200000000000000020000000000eefa5600000000000000210f6a39347ecb6ae704888bc0f7b18e2793fa23e9b9541a5c426ff1b9b7058fb9bf00000000000000020000000000ecb1f0000000000000006d0000000200000000000000020000000000ed33e400000000000000184b97dbaeb15ca23b117aba643025d038660bebf300e967b200000000000000020000000000eefa560000000000000021ac64d494ef95c4ce4486b7996cd7be90687bd09d39bac4b30edf6c781a74cdb55e5e45cd4c3e7338c358b304ca3af244f975f1178d4f3a078b000000643067683d523922626d0d3d5c7e4277094d4c3200727e282c53645c4c5917084e6e624c5b113b0b02587669484659570801374a713f43580d5715515955481749461e0b511e0a032f777e661c6f0a5312754b3f185a44052226024d200372310e1e0b102f00010101f35ba643324efa370000000155103003b8db9c2b52a037889f9b7d606a348b6ce47fecbf000000000000000028a76ba8051fef5500000024adfd996103d46200e0632994adbcc259a299386c1dd4b534ee9d55fcef9fa999360237264b5294cc0000000a000000008b825a490feb068221861e5e00000000f670b1773dea8054fb6cfc2000000000047c409d3ef1d30908a114ec0000000006fddf4e1077e195c9ed1c8f000000002f3516c818f9fd2c756b8cff000000003ec094c96c3c5621143d900a00000000548ed74c21b9c8b48e50d60f000000006a1756ac3c1c0e35a13eb36b000000006baa1b2e2cccabf0ce51e0aa000000006f2bb10a559ad4aeda04dbf50000000af55baa544950f13900000001ad34ffd0565c3f0a3c221af6bc40a2e9f55baa544950f13900000001bfea3477aeca7fef2c7959bb00f1da1df55baa544950f13900000001fe0745512ad2e12f63f40bd0e05613d9f55baa544950f1390000000100582ae56ba6feda3ad171f238b1c2c2f55baa544950f139000000010c316a89974c7548028607466f0bcaa1f55baa544950f139000000011fc8016db2243186091fca6fc31a124cf55baa544950f139000000012984525daef71f2a4b4a91a751c853fbf55baa544950f1390000000149607dc1d19a0715415377944205636ff55baa544950f1390000000164cc9ffbe502e12b034c3af518e753f4f55baa544950f139000000017ccd4084c804c9c632ef17dd07af4de40000000af55baa544950f13900000001851bd25e6690cf11f55baa544950f13900000001c6c80f812b64fb9df55baa544950f13900000001db10fc0f949ac92af55baa544950f13900000001e133af34cb5aadd1f55baa544950f13900000001e5a5bbdf22cc24b4f55baa544950f1390000000118829f487e299f25f55baa544950f139000000011c9d5d5acbd98cb0f55baa544950f139000000012380a3b742b07fe1f55baa544950f139000000012b6fdec1b1fabe83f55baa544950f1390000000139ad07f38bbb97e7648cdec4621975c19000a95acb85394c",
			"0100000000000000020000000000ed33e400000000000000186aa431a4ee0a34ba5f51f4b5d66238717435508f53a535c14fe14b0f1d3439980c0212cd525941962478cc22855bd258000000647a1960325e5f6f4315630040597d62361777247a3a3c471076274d24491d2b652c514a266235627c511e561e0f6353496111280c2e033e7e5f265e356c0c2f151f1b3c782d5a5f3158372b06737e7070281d0f19451d49495a652c7639372411701e3c6701000101f35ba643324efa370000000177fdf46cfdb565d85843590a37c1b0933e950d3b99967dba000000000000000079a953c6907392bd000000245e584aeb023271ca62a786eff4b31727b20df4736d9be7fddcbf8b5beaa75def835acc9738a3a5600000000a0000000082c5809b23348215b930e9be00000000b98cc0822c76b4f907f5db3000000000c3c1acb07f602494b57f83ee00000000f5a7e687535dce7645bc3606000000002608da1e350b6ebfe16d3a0c0000000028211ab65982b307006542680000000042d18a9b03fc8270c2ef03700000000057e760b21dd221a3866f19c500000000770387653c0fd68d96ab61b000000000783ad7c42026b40b4d38a1770000000af55baa544950f139000000019bdd750794c3941b453c93279df138c9f55baa544950f13900000001c4cfef48236f3132416be8db200fbc74f55baa544950f13900000001cc7c0810f803963553381e89c9869b67f55baa544950f13900000001cda8b5dec1abb5e727655309386aa0e4f55baa544950f139000000010b4d10a9ae885e472527e42c80030cfff55baa544950f13900000001287953ac96d2486037096c2c362d67c8f55baa544950f1390000000133b374e245a9f2446bf169b129a31dc3f55baa544950f139000000013d1aa742c9671d353ef3b51504ab34fff55baa544950f139000000014b78c627e960585313c97b4024e77683f55baa544950f139000000015084df589a1dfa6933bcf32f6faa1b940000000af55baa544950f13900000001d3236fcba0ef50dff55baa544950f13900000001d47e5e79d96bc7a9f55baa544950f13900000001db18de54a460875ef55baa544950f13900000001e51e9219241b0cb0f55baa544950f13900000001ff709edf521f5993f55baa544950f139000000010459533bf905f55ff55baa544950f1390000000111e9427f2787eae1f55baa544950f1390000000130ab42292cd442e6f55baa544950f13900000001510f2cb3f122e5dff55baa544950f1390000000151550861ea8e82a42d6452e506043de7d594317d9d4ba5c4",
			"0100000000000000020000000000ecb1f0000000000000007c0000000200000000000000020000000000ef843a00000000000000243dbcf563ee868b7f583e8f7a0f122129ecedff5c4f2aed396a4d43577ab9fc84804966e200000000000000020000000000ef843a0000000000000024620cb586ccf36c171b972e46c98b87ce2e3a2c193d79fa493bea4d3bee970cf61265fc9935aebda77d48005e17dc6124d6345bca19d06f3ed177ea2c0000006454426568557a09034e72715e484e103b3b1c63696d37713851751d476e3e61341e57675f7a5b2f4463050c5f5f0d271e3b25384558454f0565026c45270143361e430636703836035452651a177a773d2d0f6d7c32357b616e351f6b753824380f55414200000001f35ba643324efa37000000013dff16fedb1244ad75af76ee6957a8b367d94aa96c8627ab000000000000000062804be558d8ed2f00000024fa398c2bdddb14304d97c6a08e74d5debcce2f8662a9d3812e6ffee0c35f5ea6a6577296708721980000000a00000000a8df918276f2aca1414ad43400000000b626bed6126d6e2e83149b4200000000bb5868f747868db078f87e6100000000dad6956800b9d6ac40e7374500000000e69ab0726f11ef4b8db9006200000000fe41581d50c3984f55e0d4af00000000272105bc556f60b3abe5077e0000000036a6e377467f0dbe24e91193000000004c83525731cecfd2a7f01702000000004f73b95911ccb7b1a682f6670000000af55baa544950f13900000001dd5bde845c66e1011c8e2107f0a5d9daf55baa544950f13900000001f3dd3bc30dc234275653f13394a62751f55baa544950f13900000001087a9f2030cef47f34934f1edc4b0bbcf55baa544950f139000000011e8c4a6e5450fa5c22bff2a4c05271a6f55baa544950f1390000000127e8cbf92161b6e91128e713cb6acd63f55baa544950f139000000013807de3eb64c33f43b488f7ab716cf81f55baa544950f1390000000141248d407d5457915b9eeb71ec9df1d7f55baa544950f13900000001414369169b541d2766b176ab2562444ff55baa544950f139000000015eda0a92f08a86ac7d69abddd8920097f55baa544950f139000000017c4e23f7bdec39626da66e8f765f6a7b0000000af55baa544950f139000000018b837fa78976fd84f55baa544950f13900000001b135f23bc6bd399df55baa544950f13900000001b32cdfda25667449f55baa544950f13900000001df724ba62453386bf55baa544950f13900000001faf952055a363897f55baa544950f139000000011e937d3445c00831f55baa544950f139000000012caf7fc8c2ce0f34f55baa544950f1390000000165c8b60e480b726bf55baa544950f139000000017cec57afe6bddee3f55baa544950f139000000017ed1da10d58e1f570d737e094dbf1dd2dd0a6bc3e729a18a",
			"0100000000000000020000000000ecf2ea000000000000002014565e898d3d64afbb33632fd5e65940681eaf2ef6d1317f3add12dbc6c5232f6a3de00ff4cce2aa71fc4f2ad94a837a62845f18bcfee29b000000645f7e500c1d656410473713347d2e0f1353181e40056b3564266a1c270c650b122e615338731f127560415a425272676735247112384470377005297527021949213500726d69201237410646632545581249365d35020e3e1d20006b2066685f69746f3601000101f35ba643324efa37000000010b853fdb530edc093105233924f9f3207c3fe77f806c75bd00000000000000002ae481ce18647bc200000024fa3fab13193ff8c728750e9b7fd9067d07e011f3ad74e79f89c59bed6a0b68b74362dc2f377f8ba10000000a0000000089de86cf7eee53ae83e8d99d00000000916dcc4c4c42fc9c0900ea4a00000000bce6f82d52f58cb2bdd4bf2b00000000dff8360a2d80e4d00d39981a00000000f7b292641bf0f33242c6c0b20000000007dc032302526e589d5d3bf30000000025aa4e7f60dfb421324c63110000000047440f2822e4d087b0f526be0000000079b8c7a013f4165664ecc23a000000007e02731444623c4c606f887f0000000af55baa544950f13900000001898cd6eeb0c002f0243e8f34fdda48e5f55baa544950f13900000001951fbd66b594322544810fd5deacd013f55baa544950f13900000001ab52c30dd757050d17e0a08853efbbf1f55baa544950f13900000001b164035b1da841ae6e00a1b4d30679d3f55baa544950f13900000001c25d0d75305d2b3a5b193f72ca86586df55baa544950f13900000001d200ea77cbacca7a0436de7066399b16f55baa544950f13900000001d671b3da390786e944e9cf1809ba7bd9f55baa544950f139000000012ec361602818af0f4ce08a0e28694eebf55baa544950f13900000001320c18eff2c0b9a3042e02107c80c899f55baa544950f139000000017fd0e681c11c8fdf5c90ae9cd4cca6d20000000af55baa544950f139000000018608c63059db82f1f55baa544950f139000000019950227512311e3af55baa544950f13900000001a0c660e0dc325795f55baa544950f13900000001a3b84430e88972d6f55baa544950f13900000001ad3da97fcd12915af55baa544950f13900000001bcd6bc076d8ad36af55baa544950f13900000001d250e9f06e9664dff55baa544950f139000000010d874f4e98db7a85f55baa544950f1390000000115c508836d636fe4f55baa544950f139000000012c4ed7bd5cdad8a50ae3bcfa7d3bc6934d5b590de0c2a814",
			"0100000000000000020000000000ecb1f0000000000000011c0000000200000000000000020000000000ecb1f000000000000000c80000000200000000000000020000000000ecb1f000000000000000740000000200000000000000020000000000ecf2ea000000000000002078749153fca180dfe02be526eaebaa78a091b96425fe896d108e7e34b1b1f57e00000000000000020000000000ecf2ea0000000000000020ca8074629195e0b92cbb31287ac42087da2e9c8da2d11484b82b905ee548d7e800000000000000020000000000ecf2ea0000000000000020c7ce9ce2af032f21f36f51c597df55c9cf39c967b338b64870d82dcf5aeeb2e300000000000000020000000000ecf2ea00000000000000200a2641abe74b0064b1ec2266b473f562f5d21930a27867837212af7add1a1bc15217580db0cb428d2066b9ace5fd77da73c26a3d7f0e372e000000646865561f2c501c5a34653d366e293a47490b7e3b2646710961085b6c33382434173c4571222e431f2e15502e2b4f406e2c24055b475b2e1b3016251947213e54264b4c591c4e76085c2424563105374417447212597a5f3806413d385f085e0a2f343e0f01000001f35ba643324efa370000000137e41c9a8929b05b049c47b6e948f0675867ccd37f5dac94000000000000000071996a12970c91400000002454895ff09e865de96d089c886d892b2538c8a1576a49eed0fed965bf041f048c1bff6a69499bdf000000000a00000000ace2427940b3a2046b3c680f00000000c261d90601c6ffeebe7a07c6000000001810d6b65cac7aaddc8f8d65000000001a797a463a52c1e2866d78ad0000000040c9a7485bf9e90fab42fdc70000000052884f7a4ddee54dd856d45200000000561df8024e2b97a04fed0153000000005e516d9a5da24d84f851bdf800000000648e986f16fa3edd707321310000000067bf7aa257554ac36d1fa7650000000af55baa544950f139000000019c1f315e5a2b0ae41124137493e41671f55baa544950f139000000019d12fa30d6ee7048285baf404a9d8924f55baa544950f13900000001df0de31eaa079df13c9740a1b93c5432f55baa544950f13900000001e9fab8234faee441183462c19a6ac20ef55baa544950f13900000001ec0c1f453031acf87f649b15107b61bff55baa544950f139000000011636387e937075965f5bfe4996a91c0ff55baa544950f139000000012bd98a941c31cfb64b159a94787ad7b8f55baa544950f139000000013b54018507e5582057e83a2d73902ad2f55baa544950f139000000016b9b5b227dcacbb53f6299444c06fb9bf55baa544950f139000000017c7634a5bece640e099fffdaab2f93ea0000000af55baa544950f13900000001897ef79d365ce679f55baa544950f139000000018df2135986298f3df55baa544950f139000000019af7a0de19acb91ff55baa544950f13900000001b28b0cfaf84e1e70f55baa544950f13900000001d29cdd25375accd9f55baa544950f13900000001d97ab5981e698bd4f55baa544950f1390000000109e1ad9c46ffaeecf55baa544950f139000000012f877f218edcd044f55baa544950f1390000000151f5b2ff483d0066f55baa544950f13900000001762538d4f3b40546298d8a2211eac9837b5439b9b5214495",	};
}
